/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package consulo.ide.impl.idea.find.impl;

import consulo.annotation.component.ServiceImpl;
import consulo.application.ApplicationManager;
import consulo.application.util.StringSearcher;
import consulo.application.util.function.Computable;
import consulo.codeEditor.*;
import consulo.codeEditor.markup.RangeHighlighter;
import consulo.colorScheme.TextAttributesKey;
import consulo.component.messagebus.MessageBus;
import consulo.content.scope.SearchScope;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.TextEditor;
import consulo.find.*;
import consulo.ide.impl.idea.codeInsight.highlighting.HighlightManagerImpl;
import consulo.ide.impl.idea.codeInsight.hint.HintManagerImpl;
import consulo.ide.impl.idea.find.EditorSearchSession;
import consulo.ide.impl.idea.find.findUsages.FindUsagesManager;
import consulo.ide.impl.idea.find.impl.livePreview.SearchResults;
import consulo.ide.impl.idea.notification.impl.NotificationsConfigurationImpl;
import consulo.ide.impl.idea.openapi.fileTypes.impl.AbstractFileType;
import consulo.ide.impl.idea.openapi.keymap.KeymapUtil;
import consulo.ide.impl.idea.openapi.util.Comparing;
import consulo.ide.impl.idea.openapi.util.text.StringUtil;
import consulo.ide.impl.idea.ui.LightweightHint;
import consulo.ide.impl.idea.ui.ReplacePromptDialog;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.ide.impl.idea.util.text.CharArrayUtil;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.editor.highlight.DefaultSyntaxHighlighter;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighterFactory;
import consulo.language.editor.hint.HintManager;
import consulo.language.editor.ui.awt.HintUtil;
import consulo.language.file.FileViewProvider;
import consulo.language.file.LanguageFileType;
import consulo.language.internal.custom.CustomHighlighterTokenType;
import consulo.language.lexer.Lexer;
import consulo.language.parser.ParserDefinition;
import consulo.language.pattern.StringPattern;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.version.LanguageVersion;
import consulo.logging.Logger;
import consulo.navigation.NavigationItem;
import consulo.project.Project;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.IdeActions;
import consulo.usage.SyntaxHighlighterOverEditorHighlighter;
import consulo.usage.UsageViewManager;
import consulo.usage.util.ChunkExtractor;
import consulo.util.dataholder.Key;
import consulo.util.lang.ImmutableCharSequence;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
@ServiceImpl
public class FindManagerImpl extends FindManager {
  private static final Logger LOG = Logger.getInstance(FindManagerImpl.class);

  private final FindUsagesManager myFindUsagesManager;
  private boolean isFindWasPerformed = false;
  private boolean isSelectNextOccurrenceWasPerformed = false;
  private Point myReplaceInFilePromptPos = new Point(-1, -1);
  private Point myReplaceInProjectPromptPos = new Point(-1, -1);
  private final FindModel myFindInProjectModel = new FindModel();
  private final FindModel myFindInFileModel = new FindModel();
  private FindModel myFindNextModel = null;
  private FindModel myPreviousFindModel = null;
  private static final FindResultImpl NOT_FOUND_RESULT = new FindResultImpl();
  private final Project myProject;
  private final MessageBus myBus;
  private static final Key<Boolean> HIGHLIGHTER_WAS_NOT_FOUND_KEY = Key.create("consulo.ide.impl.idea.find.impl.FindManagerImpl.HighlighterNotFoundKey");

  private FindUIHelper myHelper;

  @Inject
  public FindManagerImpl(Project project, FindSettings findSettings, UsageViewManager anotherManager) {
    myProject = project;
    myBus = project.getMessageBus();
    findSettings.initModelBySetings(myFindInProjectModel);

    myFindInFileModel.setCaseSensitive(findSettings.isLocalCaseSensitive());
    myFindInFileModel.setWholeWordsOnly(findSettings.isLocalWholeWordsOnly());
    myFindInFileModel.setRegularExpressions(findSettings.isLocalRegularExpressions());

    myFindUsagesManager = new FindUsagesManager(myProject, anotherManager);
    myFindInProjectModel.setMultipleFiles(true);

    NotificationsConfigurationImpl.remove("FindInPath");
    Disposer.register(project, () -> {
      if (myHelper != null) {
        Disposer.dispose(myHelper);
      }
    });
  }

  @Override
  public FindModel createReplaceInFileModel() {
    FindModel model = new FindModel();
    model.copyFrom(getFindInFileModel());
    model.setReplaceState(true);
    model.setPromptOnReplace(false);
    return model;
  }

  @Override
  public int showPromptDialog(@Nonnull final FindModel model, String title) {
    return showPromptDialogImpl(model, title, null);
  }

  @PromptResultValue
  public int showPromptDialogImpl(@Nonnull final FindModel model, String title, @Nullable final MalformedReplacementStringException exception) {
    ReplacePromptDialog replacePromptDialog = new ReplacePromptDialog(model.isMultipleFiles(), title, myProject, exception) {
      @Override
      @Nullable
      public Point getInitialLocation() {
        if (model.isMultipleFiles() && myReplaceInProjectPromptPos.x >= 0 && myReplaceInProjectPromptPos.y >= 0) {
          return myReplaceInProjectPromptPos;
        }
        if (!model.isMultipleFiles() && myReplaceInFilePromptPos.x >= 0 && myReplaceInFilePromptPos.y >= 0) {
          return myReplaceInFilePromptPos;
        }
        return null;
      }
    };

    replacePromptDialog.show();

    if (model.isMultipleFiles()) {
      myReplaceInProjectPromptPos = replacePromptDialog.getLocation();
    }
    else {
      myReplaceInFilePromptPos = replacePromptDialog.getLocation();
    }
    return replacePromptDialog.getExitCode();
  }

  void changeGlobalSettings(FindModel findModel) {
    String stringToFind = findModel.getStringToFind();
    FindInProjectSettings findInProjectSettings = FindInProjectSettings.getInstance(myProject);

    if (!StringUtil.isEmpty(stringToFind)) {
      findInProjectSettings.addStringToFind(stringToFind);
    }
    if (!findModel.isMultipleFiles()) {
      setFindWasPerformed();
    }
    if (findModel.isReplaceState()) {
      findInProjectSettings.addStringToReplace(findModel.getStringToReplace());
    }
    if (findModel.isMultipleFiles() && !findModel.isProjectScope() && findModel.getDirectoryName() != null) {
      findInProjectSettings.addDirectory(findModel.getDirectoryName());
      myFindInProjectModel.setWithSubdirectories(findModel.isWithSubdirectories());
    }
    FindSettings.getInstance().setShowResultsInSeparateView(findModel.isOpenInNewTab());
  }

  @Override
  public void showFindDialog(@Nonnull FindModel model, @Nonnull Runnable okHandler) {
    if (myHelper == null || Disposer.isDisposed(myHelper)) {
      myHelper = new FindUIHelper(myProject, model, okHandler);
      Disposer.register(myHelper, () -> myHelper = null);
    }
    else {
      myHelper.setModel(model);
      myHelper.setOkHandler(okHandler);
    }
    myHelper.showUI();
  }

  @Override
  @Nonnull
  public FindModel getFindInFileModel() {
    return myFindInFileModel;
  }

  @Override
  @Nonnull
  public FindModel getFindInProjectModel() {
    myFindInProjectModel.setFromCursor(false);
    myFindInProjectModel.setForward(true);
    myFindInProjectModel.setGlobal(true);
    return myFindInProjectModel;
  }

  @Override
  public boolean findWasPerformed() {
    return isFindWasPerformed;
  }

  @Override
  public void setFindWasPerformed() {
    isFindWasPerformed = true;
    isSelectNextOccurrenceWasPerformed = false;
  }

  @Override
  public boolean selectNextOccurrenceWasPerformed() {
    return isSelectNextOccurrenceWasPerformed;
  }

  @Override
  public void setSelectNextOccurrenceWasPerformed() {
    isSelectNextOccurrenceWasPerformed = true;
    isFindWasPerformed = false;
  }

  @Override
  public FindModel getFindNextModel() {
    return myFindNextModel;
  }

  @Override
  public FindModel getFindNextModel(@Nonnull final Editor editor) {
    if (myFindNextModel == null) return null;

    EditorSearchSession search = EditorSearchSession.get(editor);
    if (search != null && !isSelectNextOccurrenceWasPerformed) {
      String textInField = search.getTextInField();
      if (!Comparing.equal(textInField, myFindInFileModel.getStringToFind()) && !textInField.isEmpty()) {
        FindModel patched = new FindModel();
        patched.copyFrom(myFindNextModel);
        patched.setStringToFind(textInField);
        return patched;
      }
    }

    return myFindNextModel;
  }

  @Override
  public void setFindNextModel(FindModel findNextModel) {
    myFindNextModel = findNextModel;
    myBus.syncPublisher(FindModelListener.class).findNextModelChanged();
  }

  @Override
  @Nonnull
  public FindResult findString(@Nonnull CharSequence text, int offset, @Nonnull FindModel model) {
    return findString(text, offset, model, null);
  }

  @Nonnull
  @Override
  public FindResult findString(@Nonnull CharSequence text, int offset, @Nonnull FindModel model, @Nullable VirtualFile file) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("offset=" + offset);
      LOG.debug("textlength=" + text.length());
      LOG.debug(model.toString());
    }

    return findStringLoop(text, offset, model, file, getFindContextPredicate(model, file, text));
  }

  private FindResult findStringLoop(CharSequence text, int offset, FindModel model, VirtualFile file, @Nullable Predicate<FindResult> filter) {
    final char[] textArray = CharArrayUtil.fromSequenceWithoutCopying(text);
    while (true) {
      FindResult result = doFindString(text, textArray, offset, model, file);
      if (filter == null || filter.test(result)) {
        if (!model.isWholeWordsOnly()) {
          return result;
        }
        if (!result.isStringFound()) {
          return result;
        }
        if (isWholeWord(text, result.getStartOffset(), result.getEndOffset())) {
          return result;
        }
      }

      offset = model.isForward() ? result.getStartOffset() + 1 : result.getEndOffset() - 1;
      if (offset > text.length() || offset < 0) return NOT_FOUND_RESULT;
    }
  }

  private class FindExceptCommentsOrLiteralsData implements Predicate<FindResult> {
    private final VirtualFile myFile;
    private final FindModel myFindModel;
    private final TreeMap<Integer, Integer> mySkipRangesSet;
    private final CharSequence myText;

    private FindExceptCommentsOrLiteralsData(VirtualFile file, FindModel model, CharSequence text) {
      myFile = file;
      myFindModel = model.clone();
      myText = ImmutableCharSequence.asImmutable(text);

      TreeMap<Integer, Integer> result = new TreeMap<>();

      if (model.isExceptComments() || model.isExceptCommentsAndStringLiterals()) {
        addRanges(file, model, text, result, FindModel.SearchContext.IN_COMMENTS);
      }

      if (model.isExceptStringLiterals() || model.isExceptCommentsAndStringLiterals()) {
        addRanges(file, model, text, result, FindModel.SearchContext.IN_STRING_LITERALS);
      }

      mySkipRangesSet = result;
    }

    private void addRanges(VirtualFile file, FindModel model, CharSequence text, TreeMap<Integer, Integer> result, FindModel.SearchContext searchContext) {
      FindModel clonedModel = model.clone();
      clonedModel.setSearchContext(searchContext);
      clonedModel.setForward(true);
      int offset = 0;

      while (true) {
        FindResult customResult = findStringLoop(text, offset, clonedModel, file, null);
        if (!customResult.isStringFound()) break;
        result.put(customResult.getStartOffset(), customResult.getEndOffset());
        offset = Math.max(customResult.getEndOffset(), offset + 1);  // avoid loop for zero size reg exps matches
        if (offset >= text.length()) break;
      }
    }

    boolean isAcceptableFor(FindModel model, VirtualFile file, CharSequence text) {
      return Comparing.equal(myFile, file) && myFindModel.equals(model) && myText.length() == text.length();
    }

    @Override
    public boolean test(@Nullable FindResult input) {
      if (input == null || !input.isStringFound()) return true;
      NavigableMap<Integer, Integer> map = mySkipRangesSet.headMap(input.getStartOffset(), true);
      for (Map.Entry<Integer, Integer> e : map.descendingMap().entrySet()) {
        // [e.key, e.value] intersect with [input.start, input.end]
        if (e.getKey() <= input.getStartOffset() && (input.getStartOffset() <= e.getValue() || e.getValue() >= input.getEndOffset())) return false;
        if (e.getValue() <= input.getStartOffset()) break;
      }
      return true;
    }
  }

  private static Key<FindExceptCommentsOrLiteralsData> ourExceptCommentsOrLiteralsDataKey = Key.create("except.comments.literals.search.data");

  private Predicate<FindResult> getFindContextPredicate(@Nonnull FindModel model, VirtualFile file, CharSequence text) {
    if (file == null) return null;
    FindModel.SearchContext context = model.getSearchContext();
    if (context == FindModel.SearchContext.ANY || context == FindModel.SearchContext.IN_COMMENTS || context == FindModel.SearchContext.IN_STRING_LITERALS) {
      return null;
    }

    synchronized (model) {
      FindExceptCommentsOrLiteralsData data = model.getUserData(ourExceptCommentsOrLiteralsDataKey);
      if (data == null || !data.isAcceptableFor(model, file, text)) {
        model.putUserData(ourExceptCommentsOrLiteralsDataKey, data = new FindExceptCommentsOrLiteralsData(file, model, text));
      }

      return data;
    }
  }

  @Override
  public int showMalformedReplacementPrompt(@Nonnull FindModel model, String title, MalformedReplacementStringException exception) {
    return showPromptDialogImpl(model, title, exception);
  }

  @Override
  public FindModel getPreviousFindModel() {
    return myPreviousFindModel;
  }

  @Override
  public void setPreviousFindModel(FindModel previousFindModel) {
    myPreviousFindModel = previousFindModel;
  }

  private static boolean isWholeWord(CharSequence text, int startOffset, int endOffset) {
    boolean isWordStart;

    if (startOffset != 0) {
      boolean previousCharacterIsIdentifier =
              Character.isJavaIdentifierPart(text.charAt(startOffset - 1)) && (startOffset <= 1 || text.charAt(startOffset - 2) != '\\');
      boolean previousCharacterIsSameAsNext = text.charAt(startOffset - 1) == text.charAt(startOffset);

      boolean firstCharacterIsIdentifier = Character.isJavaIdentifierPart(text.charAt(startOffset));
      isWordStart = !firstCharacterIsIdentifier && !previousCharacterIsSameAsNext || firstCharacterIsIdentifier && !previousCharacterIsIdentifier;
    }
    else {
      isWordStart = true;
    }

    boolean isWordEnd;

    if (endOffset != text.length()) {
      boolean nextCharacterIsIdentifier = Character.isJavaIdentifierPart(text.charAt(endOffset));
      boolean nextCharacterIsSameAsPrevious = endOffset > 0 && text.charAt(endOffset) == text.charAt(endOffset - 1);
      boolean lastSearchedCharacterIsIdentifier = endOffset > 0 && Character.isJavaIdentifierPart(text.charAt(endOffset - 1));

      isWordEnd = lastSearchedCharacterIsIdentifier && !nextCharacterIsIdentifier || !lastSearchedCharacterIsIdentifier && !nextCharacterIsSameAsPrevious;
    }
    else {
      isWordEnd = true;
    }

    return isWordStart && isWordEnd;
  }

  @Nonnull
  private static FindModel normalizeIfMultilined(@Nonnull FindModel findmodel) {
    if (findmodel.isMultiline()) {
      final FindModel model = new FindModel();
      model.copyFrom(findmodel);
      final String s = model.getStringToFind();
      String newStringToFind;

      if (findmodel.isRegularExpressions()) {
        newStringToFind = StringUtil.replace(s, "\n", "\\n\\s*"); // add \\s* for convenience
      }
      else {
        newStringToFind = StringUtil.escapeToRegexp(s);
        model.setRegularExpressions(true);
      }
      model.setStringToFind(newStringToFind);

      return model;
    }
    return findmodel;
  }

  @Nonnull
  private FindResult doFindString(@Nonnull CharSequence text,
                                  @Nullable char[] textArray,
                                  int offset,
                                  @Nonnull FindModel findmodel,
                                  @Nullable VirtualFile file) {
    FindModel model = normalizeIfMultilined(findmodel);
    String toFind = model.getStringToFind();
    if (toFind.isEmpty()) {
      return NOT_FOUND_RESULT;
    }

    if (model.isInCommentsOnly() || model.isInStringLiteralsOnly()) {
      if (file == null) return NOT_FOUND_RESULT;
      return findInCommentsAndLiterals(text, textArray, offset, model, file);
    }

    if (model.isRegularExpressions()) {
      return findStringByRegularExpression(text, offset, model);
    }

    final StringSearcher searcher = createStringSearcher(model);

    int index;
    if (model.isForward()) {
      final int res = searcher.scan(text, textArray, offset, text.length());
      index = res < 0 ? -1 : res;
    }
    else {
      index = offset == 0 ? -1 : searcher.scan(text, textArray, 0, offset - 1);
    }
    if (index < 0) {
      return NOT_FOUND_RESULT;
    }
    return new FindResultImpl(index, index + toFind.length());
  }

  @Nonnull
  private static StringSearcher createStringSearcher(@Nonnull FindModel model) {
    return new StringSearcher(model.getStringToFind(), model.isCaseSensitive(), model.isForward());
  }

  public static void clearPreviousFindData(FindModel model) {
    synchronized (model) {
      model.putUserData(ourCommentsLiteralsSearchDataKey, null);
      model.putUserData(ourExceptCommentsOrLiteralsDataKey, null);
    }
  }

  private static class CommentsLiteralsSearchData {
    final VirtualFile lastFile;
    int startOffset = 0;
    final SyntaxHighlighterOverEditorHighlighter highlighter;

    TokenSet tokensOfInterest;
    final StringSearcher searcher;
    final Matcher matcher;
    final Set<Language> relevantLanguages;
    final FindModel model;

    public CommentsLiteralsSearchData(VirtualFile lastFile,
                                      Set<Language> relevantLanguages,
                                      SyntaxHighlighterOverEditorHighlighter highlighter,
                                      TokenSet tokensOfInterest,
                                      StringSearcher searcher,
                                      Matcher matcher,
                                      FindModel model) {
      this.lastFile = lastFile;
      this.highlighter = highlighter;
      this.tokensOfInterest = tokensOfInterest;
      this.searcher = searcher;
      this.matcher = matcher;
      this.relevantLanguages = relevantLanguages;
      this.model = model;
    }
  }

  private static final Key<CommentsLiteralsSearchData> ourCommentsLiteralsSearchDataKey = Key.create("comments.literals.search.data");

  @Nonnull
  private FindResult findInCommentsAndLiterals(@Nonnull CharSequence text,
                                               char[] textArray,
                                               int offset,
                                               @Nonnull FindModel model,
                                               @Nonnull final VirtualFile file) {
    synchronized (model) {
      FileType ftype = file.getFileType();
      Language lang = null;
      if (ftype instanceof LanguageFileType) {
        lang = ((LanguageFileType)ftype).getLanguage();
      }

      CommentsLiteralsSearchData data = model.getUserData(ourCommentsLiteralsSearchDataKey);
      if (data == null || !Comparing.equal(data.lastFile, file) || !data.model.equals(model)) {
        SyntaxHighlighter highlighter = getHighlighter(file, lang);

        if (highlighter == null) {
          // no syntax highlighter -> no search
          return NOT_FOUND_RESULT;
        }

        TokenSet tokensOfInterest = TokenSet.EMPTY;
        Set<Language> relevantLanguages;
        if (lang != null) {
          final Language finalLang = lang;
          relevantLanguages = ApplicationManager.getApplication().runReadAction(new Computable<Set<Language>>() {
            @Override
            public Set<Language> compute() {
              Set<Language> result = new HashSet<>();

              FileViewProvider viewProvider = PsiManager.getInstance(myProject).findViewProvider(file);
              if (viewProvider != null) {
                result.addAll(viewProvider.getLanguages());
              }

              if (result.isEmpty()) {
                result.add(finalLang);
              }
              return result;
            }
          });

          for (Language relevantLanguage : relevantLanguages) {
            tokensOfInterest = addTokenTypesForLanguage(model, relevantLanguage, tokensOfInterest);
          }

          if (model.isInStringLiteralsOnly()) {
            // TODO: xml does not have string literals defined so we add XmlAttributeValue element type as convenience
            final Lexer xmlLexer = getHighlighter(null, Language.findLanguageByID("XML")).getHighlightingLexer();
            final String marker = "xxx";
            xmlLexer.start("<a href=\"" + marker + "\" />");

            while (!marker.equals(xmlLexer.getTokenText())) {
              xmlLexer.advance();
              if (xmlLexer.getTokenType() == null) break;
            }

            IElementType convenienceXmlAttrType = xmlLexer.getTokenType();
            if (convenienceXmlAttrType != null) {
              tokensOfInterest = TokenSet.orSet(tokensOfInterest, TokenSet.create(convenienceXmlAttrType));
            }
          }
        }
        else {
          relevantLanguages = ContainerUtil.newHashSet();
          if (ftype instanceof AbstractFileType) {
            if (model.isInCommentsOnly()) {
              tokensOfInterest = TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.MULTI_LINE_COMMENT);
            }
            if (model.isInStringLiteralsOnly()) {
              tokensOfInterest =
                      TokenSet.orSet(tokensOfInterest, TokenSet.create(CustomHighlighterTokenType.STRING, CustomHighlighterTokenType.SINGLE_QUOTED_STRING));
            }
          }
        }

        Matcher matcher = model.isRegularExpressions() ? compileRegExp(model, "") : null;
        StringSearcher searcher = matcher != null ? null : new StringSearcher(model.getStringToFind(), model.isCaseSensitive(), true);
        SyntaxHighlighterOverEditorHighlighter highlighterAdapter = new SyntaxHighlighterOverEditorHighlighter(highlighter, file, myProject);
        data = new CommentsLiteralsSearchData(file, relevantLanguages, highlighterAdapter, tokensOfInterest, searcher, matcher, model.clone());
        data.highlighter.restart(text);
        model.putUserData(ourCommentsLiteralsSearchDataKey, data);
      }

      int initialStartOffset = model.isForward() && data.startOffset < offset ? data.startOffset : 0;
      data.highlighter.resetPosition(initialStartOffset);
      final Lexer lexer = data.highlighter.getHighlightingLexer();

      IElementType tokenType;
      TokenSet tokens = data.tokensOfInterest;

      int lastGoodOffset = 0;
      boolean scanningForward = model.isForward();
      FindResultImpl prevFindResult = NOT_FOUND_RESULT;

      while ((tokenType = lexer.getTokenType()) != null) {
        if (lexer.getState() == 0) lastGoodOffset = lexer.getTokenStart();

        final TextAttributesKey[] keys = data.highlighter.getTokenHighlights(tokenType);

        if (tokens.contains(tokenType) ||
            (model.isInStringLiteralsOnly() && ChunkExtractor.isHighlightedAsString(keys)) ||
            (model.isInCommentsOnly() && ChunkExtractor.isHighlightedAsComment(keys))) {
          int start = lexer.getTokenStart();
          int end = lexer.getTokenEnd();
          if (model.isInStringLiteralsOnly()) { // skip literal quotes itself from matching
            char c = text.charAt(start);
            if (c == '"' || c == '\'') {
              while (start < end && c == text.charAt(start)) {
                ++start;
                if (c == text.charAt(end - 1) && start < end) --end;
              }
            }
          }

          while (true) {
            FindResultImpl findResult = null;

            if (data.searcher != null) {
              int matchStart = data.searcher.scan(text, textArray, start, end);

              if (matchStart != -1 && matchStart >= start) {
                final int matchEnd = matchStart + model.getStringToFind().length();
                if (matchStart >= offset || !scanningForward) {
                  findResult = new FindResultImpl(matchStart, matchEnd);
                }
                else {
                  start = matchEnd;
                  continue;
                }
              }
            }
            else if (start <= end) {
              data.matcher.reset(StringPattern.newBombedCharSequence(text.subSequence(start, end)));
              if (data.matcher.find()) {
                final int matchEnd = start + data.matcher.end();
                int matchStart = start + data.matcher.start();
                if (matchStart >= offset || !scanningForward) {
                  findResult = new FindResultImpl(matchStart, matchEnd);
                }
                else {
                  int diff = 0;
                  if (start == end) {
                    diff = scanningForward ? 1 : -1;
                  }
                  start = matchEnd + diff;
                  continue;
                }
              }
            }

            if (findResult != null) {
              if (scanningForward) {
                data.startOffset = lastGoodOffset;
                return findResult;
              }
              else {

                if (findResult.getEndOffset() >= offset) return prevFindResult;
                prevFindResult = findResult;
                start = findResult.getEndOffset();
                continue;
              }
            }
            break;
          }
        }
        else {
          Language tokenLang = tokenType.getLanguage();
          if (tokenLang != lang && tokenLang != Language.ANY && !data.relevantLanguages.contains(tokenLang)) {
            tokens = addTokenTypesForLanguage(model, tokenLang, tokens);
            data.tokensOfInterest = tokens;
            data.relevantLanguages.add(tokenLang);
          }
        }

        lexer.advance();
      }

      return prevFindResult;
    }
  }

  private static TokenSet addTokenTypesForLanguage(FindModel model, Language lang, TokenSet tokensOfInterest) {
    ParserDefinition definition = ParserDefinition.forLanguage(lang);
    if (definition != null) {
      for (LanguageVersion languageVersion : lang.getVersions()) {
        tokensOfInterest = TokenSet.orSet(tokensOfInterest, model.isInCommentsOnly() ? definition.getCommentTokens(languageVersion) : TokenSet.EMPTY);
        tokensOfInterest =
                TokenSet.orSet(tokensOfInterest, model.isInStringLiteralsOnly() ? definition.getStringLiteralElements(languageVersion) : TokenSet.EMPTY);
      }
    }
    return tokensOfInterest;
  }

  private static
  @Nullable
  SyntaxHighlighter getHighlighter(VirtualFile file, @Nullable Language lang) {
    SyntaxHighlighter syntaxHighlighter = lang != null ? SyntaxHighlighterFactory.getSyntaxHighlighter(lang, null, file) : null;
    if (lang == null || syntaxHighlighter instanceof DefaultSyntaxHighlighter) {
      syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(file.getFileType(), null, file);
    }

    return syntaxHighlighter;
  }

  private static FindResult findStringByRegularExpression(CharSequence text, int startOffset, FindModel model) {
    Matcher matcher = compileRegExp(model, text);
    if (matcher == null) {
      return NOT_FOUND_RESULT;
    }
    try {
      if (model.isForward()) {
        if (matcher.find(startOffset)) {
          if (matcher.end() <= text.length()) {
            return new FindResultImpl(matcher.start(), matcher.end());
          }
        }
        return NOT_FOUND_RESULT;
      }
      else {
        int start = -1;
        int end = -1;
        while (matcher.find() && matcher.end() < startOffset) {
          start = matcher.start();
          end = matcher.end();
        }
        if (start < 0) {
          return NOT_FOUND_RESULT;
        }
        return new FindResultImpl(start, end);
      }
    }
    catch (StackOverflowError soe) {
      return NOT_FOUND_RESULT;
    }
  }

  private static Matcher compileRegExp(FindModel model, CharSequence text) {
    Pattern pattern = model.compileRegExp();
    return pattern == null ? null : pattern.matcher(StringPattern.newBombedCharSequence(text));
  }

  @Override
  public String getStringToReplace(@Nonnull String foundString, @Nonnull FindModel model, int startOffset, @Nonnull CharSequence documentText)
          throws MalformedReplacementStringException {
    String toReplace = model.getStringToReplace();
    if (model.isRegularExpressions()) {
      return getStringToReplaceByRegexp(model, documentText, startOffset);
    }
    if (model.isPreserveCase()) {
      return replaceWithCaseRespect(toReplace, foundString);
    }
    return toReplace;
  }

  private static String getStringToReplaceByRegexp(@Nonnull final FindModel model, @Nonnull CharSequence text, int startOffset)
          throws MalformedReplacementStringException {
    Matcher matcher = compileRegexAndFindFirst(model, text, startOffset);
    return getStringToReplaceByRegexp(model, matcher);
  }

  private static String getStringToReplaceByRegexp(@Nonnull final FindModel model, Matcher matcher) throws MalformedReplacementStringException {
    if (matcher == null) return null;
    try {
      String toReplace = model.getStringToReplace();
      return new RegExReplacementBuilder(matcher).createReplacement(toReplace);
    }
    catch (Exception e) {
      throw createMalformedReplacementException(model, e);
    }
  }

  private static Matcher compileRegexAndFindFirst(FindModel model, CharSequence text, int startOffset) {
    model = normalizeIfMultilined(model);
    Matcher matcher = compileRegExp(model, text);

    if (model.isForward()) {
      if (!matcher.find(startOffset)) {
        return null;
      }
      if (matcher.end() > text.length()) {
        return null;
      }
    }
    else {
      int start = -1;
      while (matcher.find() && matcher.end() < startOffset) {
        start = matcher.start();
      }
      if (start < 0) {
        return null;
      }
    }
    return matcher;
  }

  private static MalformedReplacementStringException createMalformedReplacementException(FindModel model, Exception e) {
    return new MalformedReplacementStringException(FindBundle.message("find.replace.invalid.replacement.string", model.getStringToReplace()), e);
  }

  private static String replaceWithCaseRespect(String toReplace, String foundString) {
    if (foundString.isEmpty() || toReplace.isEmpty()) return toReplace;
    StringBuilder buffer = new StringBuilder();

    if (Character.isUpperCase(foundString.charAt(0))) {
      buffer.append(Character.toUpperCase(toReplace.charAt(0)));
    }
    else {
      buffer.append(Character.toLowerCase(toReplace.charAt(0)));
    }

    if (toReplace.length() == 1) return buffer.toString();

    if (foundString.length() == 1) {
      buffer.append(toReplace.substring(1));
      return buffer.toString();
    }

    boolean isReplacementLowercase = true;
    boolean isReplacementUppercase = true;
    for (int i = 1; i < toReplace.length(); i++) {
      char replacementChar = toReplace.charAt(i);
      if (!Character.isLetter(replacementChar)) continue;
      isReplacementLowercase &= Character.isLowerCase(replacementChar);
      isReplacementUppercase &= Character.isUpperCase(replacementChar);
      if (!isReplacementLowercase && !isReplacementUppercase) break;
    }

    boolean isTailUpper = true;
    boolean isTailLower = true;
    for (int i = 1; i < foundString.length(); i++) {
      char foundChar = foundString.charAt(i);
      if (!Character.isLetter(foundChar)) continue;
      isTailUpper &= Character.isUpperCase(foundChar);
      isTailLower &= Character.isLowerCase(foundChar);
      if (!isTailUpper && !isTailLower) break;
    }

    if (isTailUpper && (isReplacementLowercase || isReplacementUppercase)) {
      buffer.append(StringUtil.toUpperCase(toReplace.substring(1)));
    }
    else if (isTailLower && (isReplacementLowercase || isReplacementUppercase)) {
      buffer.append(toReplace.substring(1).toLowerCase());
    }
    else {
      buffer.append(toReplace.substring(1));
    }
    return buffer.toString();
  }

  @Override
  public boolean canFindUsages(@Nonnull PsiElement element) {
    return element.isValid() && myFindUsagesManager.canFindUsages(element);
  }

  @Override
  public void findUsages(@Nonnull PsiElement element) {
    findUsages(element, false);
  }

  @Override
  public void findUsagesInScope(@Nonnull PsiElement element, @Nonnull SearchScope searchScope) {
    myFindUsagesManager.findUsages(element, null, null, false, searchScope);
  }

  @Override
  public void findUsages(@Nonnull PsiElement element, boolean showDialog) {
    myFindUsagesManager.findUsages(element, null, null, showDialog, null);
  }

  @Override
  public void showSettingsAndFindUsages(@Nonnull NavigationItem[] targets) {
    FindUsagesManager.showSettingsAndFindUsages(targets);
  }

  @Override
  public void clearFindingNextUsageInFile() {
    myFindUsagesManager.clearFindingNextUsageInFile();
  }

  @Override
  public void findUsagesInEditor(@Nonnull PsiElement element, @Nonnull FileEditor fileEditor) {
    if (fileEditor instanceof TextEditor) {
      TextEditor textEditor = (TextEditor)fileEditor;
      Editor editor = textEditor.getEditor();
      Document document = editor.getDocument();
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);

      myFindUsagesManager.findUsages(element, psiFile, fileEditor, false, null);
    }
  }

  private static boolean tryToFindNextUsageViaEditorSearchComponent(Editor editor, SearchResults.Direction forwardOrBackward) {
    EditorSearchSession search = EditorSearchSession.get(editor);
    if (search != null && search.hasMatches()) {
      if (forwardOrBackward == SearchResults.Direction.UP) {
        search.searchBackward();
      }
      else {
        search.searchForward();
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean findNextUsageInEditor(@Nonnull FileEditor fileEditor) {
    return findNextUsageInFile(fileEditor, SearchResults.Direction.DOWN);
  }

  private boolean findNextUsageInFile(@Nonnull FileEditor fileEditor, @Nonnull SearchResults.Direction direction) {
    if (fileEditor instanceof TextEditor) {
      TextEditor textEditor = (TextEditor)fileEditor;
      Editor editor = textEditor.getEditor();
      editor.getCaretModel().removeSecondaryCarets();
      if (tryToFindNextUsageViaEditorSearchComponent(editor, direction)) {
        return true;
      }

      RangeHighlighter[] highlighters = ((HighlightManagerImpl)HighlightManager.getInstance(myProject)).getHighlighters(editor);
      if (highlighters.length > 0) {
        return highlightNextHighlighter(highlighters, editor, editor.getCaretModel().getOffset(), direction == SearchResults.Direction.DOWN, false);
      }
    }

    if (direction == SearchResults.Direction.DOWN) {
      return myFindUsagesManager.findNextUsageInFile(fileEditor);
    }
    return myFindUsagesManager.findPreviousUsageInFile(fileEditor);
  }

  @Override
  public boolean findPreviousUsageInEditor(@Nonnull FileEditor fileEditor) {
    return findNextUsageInFile(fileEditor, SearchResults.Direction.UP);
  }

  @Nullable
  @Override
  public FindUsagesHandler getFindUsagesHandler(@Nonnull PsiElement element, boolean forHighlightUsages) {
    final FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(myProject)).getFindUsagesManager();
    return findUsagesManager.getFindUsagesHandler(element, forHighlightUsages);
  }

  private static boolean highlightNextHighlighter(RangeHighlighter[] highlighters, Editor editor, int offset, boolean isForward, boolean secondPass) {
    RangeHighlighter highlighterToSelect = null;
    Object wasNotFound = editor.getUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY);
    for (RangeHighlighter highlighter : highlighters) {
      int start = highlighter.getStartOffset();
      int end = highlighter.getEndOffset();
      if (highlighter.isValid() && start < end) {
        if (isForward && (start > offset || start == offset && secondPass)) {
          if (highlighterToSelect == null || highlighterToSelect.getStartOffset() > start) highlighterToSelect = highlighter;
        }
        if (!isForward && (end < offset || end == offset && secondPass)) {
          if (highlighterToSelect == null || highlighterToSelect.getEndOffset() < end) highlighterToSelect = highlighter;
        }
      }
    }
    if (highlighterToSelect != null) {
      expandFoldRegionsIfNecessary(editor, highlighterToSelect.getStartOffset(), highlighterToSelect.getEndOffset());
      editor.getSelectionModel().setSelection(highlighterToSelect.getStartOffset(), highlighterToSelect.getEndOffset());
      editor.getCaretModel().moveToOffset(highlighterToSelect.getStartOffset());
      ScrollType scrollType;
      if (secondPass) {
        scrollType = isForward ? ScrollType.CENTER_UP : ScrollType.CENTER_DOWN;
      }
      else {
        scrollType = isForward ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
      }
      editor.getScrollingModel().scrollToCaret(scrollType);
      editor.putUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY, null);
      return true;
    }

    if (wasNotFound == null) {
      editor.putUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY, Boolean.TRUE);
      String message = FindBundle.message("find.highlight.no.more.highlights.found");
      if (isForward) {
        AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT);
        String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
        if (shortcutsText.isEmpty()) {
          message = FindBundle.message("find.search.again.from.top.action.message", message);
        }
        else {
          message = FindBundle.message("find.search.again.from.top.hotkey.message", message, shortcutsText);
        }
      }
      else {
        AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_PREVIOUS);
        String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
        if (shortcutsText.isEmpty()) {
          message = FindBundle.message("find.search.again.from.bottom.action.message", message);
        }
        else {
          message = FindBundle.message("find.search.again.from.bottom.hotkey.message", message, shortcutsText);
        }
      }
      JComponent component = HintUtil.createInformationLabel(message);
      final LightweightHint hint = new LightweightHint(component);
      HintManagerImpl.getInstanceImpl()
              .showEditorHint(hint, editor, HintManager.UNDER, HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0,
                              false);
      return true;
    }
    if (!secondPass) {
      offset = isForward ? 0 : editor.getDocument().getTextLength();
      return highlightNextHighlighter(highlighters, editor, offset, isForward, true);
    }

    return false;
  }

  private static void expandFoldRegionsIfNecessary(@Nonnull Editor editor, final int startOffset, int endOffset) {
    final FoldingModel foldingModel = editor.getFoldingModel();
    final FoldRegion[] regions;
    if (foldingModel instanceof FoldingModelEx) {
      regions = ((FoldingModelEx)foldingModel).fetchTopLevel();
    }
    else {
      regions = foldingModel.getAllFoldRegions();
    }
    if (regions == null) {
      return;
    }
    int i = Arrays.binarySearch(regions, null, (o1, o2) -> {
      // Find the first region that ends after the given start offset
      if (o1 == null) {
        return startOffset - o2.getEndOffset();
      }
      else {
        return o1.getEndOffset() - startOffset;
      }
    });
    if (i < 0) {
      i = -i - 1;
    }
    else {
      i++; // Don't expand fold region that ends at the start offset.
    }
    if (i >= regions.length) {
      return;
    }
    final List<FoldRegion> toExpand = new ArrayList<>();
    for (; i < regions.length; i++) {
      final FoldRegion region = regions[i];
      if (region.getStartOffset() >= endOffset) {
        break;
      }
      if (!region.isExpanded()) {
        toExpand.add(region);
      }
    }
    if (toExpand.isEmpty()) {
      return;
    }
    foldingModel.runBatchFoldingOperation(() -> {
      for (FoldRegion region : toExpand) {
        region.setExpanded(true);
      }
    });
  }

  @Nonnull
  public FindUsagesManager getFindUsagesManager() {
    return myFindUsagesManager;
  }
}
