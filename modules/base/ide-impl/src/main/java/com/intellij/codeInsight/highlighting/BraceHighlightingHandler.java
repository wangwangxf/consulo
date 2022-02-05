/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Sep 27, 2002
 * Time: 3:10:17 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.injected.editor.EditorWindow;
import consulo.editor.markup.HighlighterTargetArea;
import consulo.editor.markup.LineMarkerRenderer;
import consulo.editor.markup.RangeHighlighter;
import consulo.editor.markup.TextAttributes;
import consulo.language.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import consulo.document.Document;
import consulo.editor.Editor;
import consulo.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import consulo.editor.colorScheme.EditorColorsManager;
import consulo.editor.colorScheme.EditorColorsScheme;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.util.HighlighterIteratorWrapper;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import consulo.editor.highlighter.EditorHighlighter;
import consulo.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import consulo.language.plain.psi.PsiPlainTextFile;
import consulo.language.psi.*;
import consulo.virtualFileSystem.fileType.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import consulo.application.dumb.DumbAwareRunnable;
import consulo.project.Project;
import consulo.util.lang.function.Conditions;
import consulo.document.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import consulo.language.ast.IElementType;
import consulo.language.ast.ILazyParseableElementType;
import com.intellij.psi.util.PsiUtilBase;
import consulo.language.psi.PsiUtilCore;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Alarm;
import consulo.application.util.function.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import consulo.ui.ex.awt.TargetAWT;
import consulo.ui.color.ColorValue;
import consulo.ui.util.ColorValueUtil;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class BraceHighlightingHandler {
  private static final Key<List<RangeHighlighter>> BRACE_HIGHLIGHTERS_IN_EDITOR_VIEW_KEY = Key.create("BraceHighlighter.BRACE_HIGHLIGHTERS_IN_EDITOR_VIEW_KEY");
  private static final Key<RangeHighlighter> LINE_MARKER_IN_EDITOR_KEY = Key.create("BraceHighlighter.LINE_MARKER_IN_EDITOR_KEY");
  private static final Key<LightweightHint> HINT_IN_EDITOR_KEY = Key.create("BraceHighlighter.HINT_IN_EDITOR_KEY");

  /**
   * Holds weak references to the editors that are being processed at non-EDT.
   * <p/>
   * Is intended to be used to avoid submitting unnecessary new processing request from EDT, i.e. it's assumed that the collection
   * is accessed from the single thread (EDT).
   */
  private static final Set<Editor> PROCESSED_EDITORS = Collections.newSetFromMap(ContainerUtil.createWeakMap());

  @Nonnull
  private final Project myProject;
  @Nonnull
  private final Editor myEditor;
  private final Alarm myAlarm;

  private final DocumentEx myDocument;
  private final PsiFile myPsiFile;
  private final CodeInsightSettings myCodeInsightSettings;

  private BraceHighlightingHandler(@Nonnull Project project, @Nonnull Editor editor, @Nonnull Alarm alarm, PsiFile psiFile) {
    myProject = project;

    myEditor = editor;
    myAlarm = alarm;
    myDocument = (DocumentEx)myEditor.getDocument();

    myPsiFile = psiFile;
    myCodeInsightSettings = CodeInsightSettings.getInstance();
  }

  static void lookForInjectedAndMatchBracesInOtherThread(@Nonnull final Editor editor,
                                                         @Nonnull final Alarm alarm,
                                                         @Nonnull final Processor<BraceHighlightingHandler> processor) {
    ApplicationEx application = (ApplicationEx)Application.get();
    application.assertIsDispatchThread();
    if (!isValidEditor(editor)) return;
    if (!PROCESSED_EDITORS.add(editor)) {
      // Skip processing if that is not really necessary.
      // Assuming to be in EDT here.
      return;
    }
    final int offset = editor.getCaretModel().getOffset();
    final Project project = editor.getProject();
    final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (!isValidFile(psiFile)) return;
    application.executeOnPooledThread(() -> {
      if (!application.tryRunReadAction(() -> {
        final PsiFile injected;
        try {
          if (psiFile instanceof PsiBinaryFile || !isValidEditor(editor) || !isValidFile(psiFile)) {
            injected = null;
          }
          else {
            injected = getInjectedFileIfAny(editor, project, offset, psiFile, alarm);
          }
        }
        catch (RuntimeException e) {
          // Reset processing flag in case of unexpected exception.
          application.invokeLater(new DumbAwareRunnable() {
            @Override
            public void run() {
              PROCESSED_EDITORS.remove(editor);
            }
          });
          throw e;
        }
        application.invokeLater(new DumbAwareRunnable() {
          @Override
          public void run() {
            try {
              if (isValidEditor(editor) && isValidFile(injected)) {
                Editor newEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, injected);
                BraceHighlightingHandler handler = new BraceHighlightingHandler(project, newEditor, alarm, injected);
                processor.process(handler);
              }
            }
            finally {
              PROCESSED_EDITORS.remove(editor);
            }
          }
        }, ModalityState.stateForComponent(editor.getComponent()));
      })) {
        // write action is queued in AWT. restart after it's finished
        application.invokeLater(() -> {
          PROCESSED_EDITORS.remove(editor);
          lookForInjectedAndMatchBracesInOtherThread(editor, alarm, processor);
        }, ModalityState.stateForComponent(editor.getComponent()));
      }
    });
  }

  private static boolean isValidFile(PsiFile file) {
    return file != null && file.isValid() && !file.getProject().isDisposed();
  }

  private static boolean isValidEditor(@Nonnull Editor editor) {
    Project editorProject = editor.getProject();
    return editorProject != null && !editorProject.isDisposed() && !editor.isDisposed() && editor.isShowing() && !editor.isViewer();
  }

  @Nonnull
  private static PsiFile getInjectedFileIfAny(@Nonnull final Editor editor,
                                              @Nonnull final Project project,
                                              int offset,
                                              @Nonnull PsiFile psiFile,
                                              @Nonnull final Alarm alarm) {
    Document document = editor.getDocument();
    // when document is committed, try to highlight braces in injected lang - it's fast
    if (PsiDocumentManager.getInstance(project).isCommitted(document)) {
      final PsiElement injectedElement = InjectedLanguageManager.getInstance(psiFile.getProject()).findInjectedElementAt(psiFile, offset);
      if (injectedElement != null /*&& !(injectedElement instanceof PsiWhiteSpace)*/) {
        final PsiFile injected = injectedElement.getContainingFile();
        if (injected != null) {
          return injected;
        }
      }
    }
    else {
      PsiDocumentManager.getInstance(project).performForCommittedDocument(document, () -> {
        if (!project.isDisposed() && !editor.isDisposed()) {
          BraceHighlighter.updateBraces(editor, alarm);
        }
      });
    }
    return psiFile;
  }

  @Nonnull
  static EditorHighlighter getLazyParsableHighlighterIfAny(Project project, Editor editor, PsiFile psiFile) {
    if (!PsiDocumentManager.getInstance(project).isCommitted(editor.getDocument())) {
      return ((EditorEx)editor).getHighlighter();
    }
    PsiElement elementAt = psiFile.findElementAt(editor.getCaretModel().getOffset());
    for (PsiElement e : SyntaxTraverser.psiApi().parents(elementAt).takeWhile(Conditions.notEqualTo(psiFile))) {
      if (!(PsiUtilCore.getElementType(e) instanceof ILazyParseableElementType)) continue;
      Language language = ILazyParseableElementType.LANGUAGE_KEY.get(e.getNode());
      if (language == null) continue;
      TextRange range = e.getTextRange();
      final int offset = range.getStartOffset();
      SyntaxHighlighter syntaxHighlighter =
              SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, psiFile.getVirtualFile());
      LexerEditorHighlighter highlighter = new LexerEditorHighlighter(syntaxHighlighter, editor.getColorsScheme()) {
        @Nonnull
        @Override
        public HighlighterIterator createIterator(int startOffset) {
          return new HighlighterIteratorWrapper(super.createIterator(Math.max(startOffset - offset, 0))) {
            @Override
            public int getStart() {
              return super.getStart() + offset;
            }

            @Override
            public int getEnd() {
              return super.getEnd() + offset;
            }
          };
        }
      };
      highlighter.setText(editor.getDocument().getText(range));
      return highlighter;
    }
    return ((EditorEx)editor).getHighlighter();
  }

  void updateBraces() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myPsiFile == null || !myPsiFile.isValid()) return;

    clearBraceHighlighters();

    if (!myCodeInsightSettings.HIGHLIGHT_BRACES) return;

    if (myEditor.getSelectionModel().hasSelection()) return;

    if (myEditor.getSoftWrapModel().isInsideOrBeforeSoftWrap(myEditor.getCaretModel().getVisualPosition())) return;

    int offset = myEditor.getCaretModel().getOffset();
    final CharSequence chars = myEditor.getDocument().getCharsSequence();

    //if (myEditor.offsetToLogicalPosition(offset).column != myEditor.getCaretModel().getLogicalPosition().column) {
    //  // we are in virtual space
    //  final int caretLineNumber = myEditor.getCaretModel().getLogicalPosition().line;
    //  if (caretLineNumber >= myDocument.getLineCount()) return;
    //  offset = myDocument.getLineEndOffset(caretLineNumber) + myDocument.getLineSeparatorLength(caretLineNumber);
    //}

    final int originalOffset = offset;

    EditorHighlighter highlighter = getEditorHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset);
    FileType fileType = PsiUtilBase.getPsiFileAtOffset(myPsiFile, offset).getFileType();

    if (iterator.atEnd()) {
      offset--;
    }
    else if (BraceMatchingUtil.isRBraceToken(iterator, chars, fileType)) {
      offset--;
    }
    else if (!BraceMatchingUtil.isLBraceToken(iterator, chars, fileType)) {
      offset--;

      if (offset >= 0) {
        HighlighterIterator it = highlighter.createIterator(offset);
        if (!BraceMatchingUtil.isRBraceToken(it, chars, getFileTypeByIterator(it))) offset++;
      }
    }

    if (offset < 0) {
      removeLineMarkers();
      return;
    }

    iterator = highlighter.createIterator(offset);
    fileType = getFileTypeByIterator(iterator);

    myAlarm.cancelAllRequests();

    if (BraceMatchingUtil.isLBraceToken(iterator, chars, fileType) ||
        BraceMatchingUtil.isRBraceToken(iterator, chars, fileType)) {
      doHighlight(offset, originalOffset, fileType);
    }
    else if (offset > 0 && offset < chars.length()) {
      // There is a possible case that there are paired braces nearby the caret position and the document contains only white
      // space symbols between them. We want to highlight such braces as well.
      // Example:
      //     public void test() { <caret>
      //     }
      char c = chars.charAt(offset);
      boolean searchForward = c != '\n';

      // Try to find matched brace backwards.
      if (offset >= originalOffset && (c == ' ' || c == '\t' || c == '\n')) {
        int backwardNonWsOffset = CharArrayUtil.shiftBackward(chars, offset - 1, "\t ");
        if (backwardNonWsOffset >= 0) {
          iterator = highlighter.createIterator(backwardNonWsOffset);
          FileType newFileType = getFileTypeByIterator(iterator);
          if (BraceMatchingUtil.isLBraceToken(iterator, chars, newFileType) ||
              BraceMatchingUtil.isRBraceToken(iterator, chars, newFileType)) {
            offset = backwardNonWsOffset;
            searchForward = false;
            doHighlight(backwardNonWsOffset, originalOffset, newFileType);
          }
        }
      }

      // Try to find matched brace forward.
      if (searchForward) {
        int forwardOffset = CharArrayUtil.shiftForward(chars, offset, "\t ");
        if (forwardOffset > offset || c == ' ' || c == '\t') {
          iterator = highlighter.createIterator(forwardOffset);
          FileType newFileType = getFileTypeByIterator(iterator);
          if (BraceMatchingUtil.isLBraceToken(iterator, chars, newFileType) ||
              BraceMatchingUtil.isRBraceToken(iterator, chars, newFileType)) {
            offset = forwardOffset;
            doHighlight(forwardOffset, originalOffset, newFileType);
          }
        }
      }
    }

    //highlight scope
    if (!myCodeInsightSettings.HIGHLIGHT_SCOPE) {
      removeLineMarkers();
      return;
    }

    final int _offset = offset;
    final FileType _fileType = fileType;
    myAlarm.addRequest(() -> {
      if (!myProject.isDisposed() && !myEditor.isDisposed()) {
        highlightScope(_offset, _fileType);
      }
    }, 300);
  }

  @Nonnull
  private FileType getFileTypeByIterator(@Nonnull HighlighterIterator iterator) {
    return PsiUtilBase.getPsiFileAtOffset(myPsiFile, iterator.getStart()).getFileType();
  }

  @Nonnull
  private FileType getFileTypeByOffset(int offset) {
    return PsiUtilBase.getPsiFileAtOffset(myPsiFile, offset).getFileType();
  }

  @Nonnull
  private EditorHighlighter getEditorHighlighter() {
    return getLazyParsableHighlighterIfAny(myProject, myEditor, myPsiFile);
  }

  private void highlightScope(int offset, @Nonnull FileType fileType) {
    if (myEditor.getFoldingModel().isOffsetCollapsed(offset)) return;
    if (myEditor.getDocument().getTextLength() <= offset) return;

    HighlighterIterator iterator = getEditorHighlighter().createIterator(offset);
    final CharSequence chars = myDocument.getCharsSequence();

    if (!BraceMatchingUtil.isStructuralBraceToken(fileType, iterator, chars)) {
//      if (BraceMatchingUtil.isRBraceTokenToHighlight(myFileType, iterator) || BraceMatchingUtil.isLBraceTokenToHighlight(myFileType, iterator)) return;
    }
    else {
      if (BraceMatchingUtil.isRBraceToken(iterator, chars, fileType) ||
          BraceMatchingUtil.isLBraceToken(iterator, chars, fileType)) return;
    }

    if (!BraceMatchingUtil.findStructuralLeftBrace(fileType, iterator, chars)) {
      removeLineMarkers();
      return;
    }

    highlightLeftBrace(iterator, true, fileType);
  }

  private void doHighlight(int offset, int originalOffset, @Nonnull FileType fileType) {
    if (myEditor.getFoldingModel().isOffsetCollapsed(offset)) return;

    HighlighterIterator iterator = getEditorHighlighter().createIterator(offset);
    final CharSequence chars = myDocument.getCharsSequence();

    if (BraceMatchingUtil.isLBraceToken(iterator, chars, fileType)) {
      IElementType tokenType = (IElementType)iterator.getTokenType();

      iterator.advance();
      if (!iterator.atEnd() && BraceMatchingUtil.isRBraceToken(iterator, chars, fileType)) {
        if (BraceMatchingUtil.isPairBraces(tokenType, (IElementType)iterator.getTokenType(), fileType) &&
            originalOffset == iterator.getStart()) return;
      }

      iterator.retreat();
      highlightLeftBrace(iterator, false, fileType);

      if (offset > 0) {
        iterator = getEditorHighlighter().createIterator(offset - 1);
        if (BraceMatchingUtil.isRBraceToken(iterator, chars, fileType)) {
          highlightRightBrace(iterator, fileType);
        }
      }
    }
    else if (BraceMatchingUtil.isRBraceToken(iterator, chars, fileType)) {
      highlightRightBrace(iterator, fileType);
    }
  }

  private void highlightRightBrace(@Nonnull HighlighterIterator iterator, @Nonnull FileType fileType) {
    TextRange brace1 = TextRange.create(iterator.getStart(), iterator.getEnd());

    boolean matched = BraceMatchingUtil.matchBrace(myDocument.getCharsSequence(), fileType, iterator, false);

    TextRange brace2 = iterator.atEnd() ? null : TextRange.create(iterator.getStart(), iterator.getEnd());

    highlightBraces(brace2, brace1, matched, false, fileType);
  }

  private void highlightLeftBrace(@Nonnull HighlighterIterator iterator, boolean scopeHighlighting, @Nonnull FileType fileType) {
    TextRange brace1Start = TextRange.create(iterator.getStart(), iterator.getEnd());
    boolean matched = BraceMatchingUtil.matchBrace(myDocument.getCharsSequence(), fileType, iterator, true);

    TextRange brace2End = iterator.atEnd() ? null : TextRange.create(iterator.getStart(), iterator.getEnd());

    highlightBraces(brace1Start, brace2End, matched, scopeHighlighting, fileType);
  }

  private void highlightBraces(@Nullable TextRange lBrace, @Nullable TextRange rBrace, boolean matched, boolean scopeHighlighting, @Nonnull FileType fileType) {
    if (!matched && fileType == PlainTextFileType.INSTANCE) {
      return;
    }

    EditorColorsScheme scheme = myEditor.getColorsScheme();
    final TextAttributes attributes =
            matched ? scheme.getAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES)
                    : scheme.getAttributes(CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES);

    if (rBrace != null && !scopeHighlighting) {
      highlightBrace(rBrace, matched);
    }

    if (lBrace != null && !scopeHighlighting) {
      highlightBrace(lBrace, matched);
    }

    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject); // null in default project
    if (fileEditorManager == null || !myEditor.equals(fileEditorManager.getSelectedTextEditor())) {
      return;
    }

    if (lBrace != null && rBrace !=null) {
      final int startLine = myEditor.offsetToLogicalPosition(lBrace.getStartOffset()).line;
      final int endLine = myEditor.offsetToLogicalPosition(rBrace.getEndOffset()).line;
      if (endLine - startLine > 0) {
        final Runnable runnable = () -> {
          if (myProject.isDisposed() || myEditor.isDisposed()) return;
          ColorValue color = attributes.getBackgroundColor();
          if (color == null) return;
          color = ColorValueUtil.isDark(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground()) ? ColorValueUtil.shift(color, 1.5d) : ColorValueUtil.darker(color);
          lineMarkFragment(startLine, endLine, color);
        };

        if (!scopeHighlighting) {
          myAlarm.addRequest(runnable, 300);
        }
        else {
          runnable.run();
        }
      }
      else {
        removeLineMarkers();
      }

      if (!scopeHighlighting) {
        showScopeHint(lBrace.getStartOffset(), lBrace.getEndOffset());
      }
    }
    else {
      if (!myCodeInsightSettings.HIGHLIGHT_SCOPE) {
        removeLineMarkers();
      }
    }
  }

  private void highlightBrace(@Nonnull TextRange braceRange, boolean matched) {
    EditorColorsScheme scheme = myEditor.getColorsScheme();
    final TextAttributes attributes =
            matched ? scheme.getAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES)
                    : scheme.getAttributes(CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES);


    RangeHighlighter rbraceHighlighter =
            myEditor.getMarkupModel().addRangeHighlighter(
                    braceRange.getStartOffset(), braceRange.getEndOffset(), HighlighterLayer.LAST + 1, attributes, HighlighterTargetArea.EXACT_RANGE);
    rbraceHighlighter.setGreedyToLeft(false);
    rbraceHighlighter.setGreedyToRight(false);
    registerHighlighter(rbraceHighlighter);
  }

  private void registerHighlighter(@Nonnull RangeHighlighter highlighter) {
    getHighlightersList().add(highlighter);
  }

  @Nonnull
  private List<RangeHighlighter> getHighlightersList() {
    // braces are highlighted across the whole editor, not in each injected editor separately
    Editor editor = myEditor instanceof EditorWindow ? ((EditorWindow)myEditor).getDelegate() : myEditor;
    List<RangeHighlighter> highlighters = editor.getUserData(BRACE_HIGHLIGHTERS_IN_EDITOR_VIEW_KEY);
    if (highlighters == null) {
      highlighters = new ArrayList<>();
      editor.putUserData(BRACE_HIGHLIGHTERS_IN_EDITOR_VIEW_KEY, highlighters);
    }
    return highlighters;
  }

  private void showScopeHint(final int lbraceStart, final int lbraceEnd) {
    LogicalPosition bracePosition = myEditor.offsetToLogicalPosition(lbraceStart);
    Point braceLocation = myEditor.logicalPositionToXY(bracePosition);
    final int y = braceLocation.y;
    myAlarm.addRequest(() -> {
      if (myProject.isDisposed()) return;
      PsiDocumentManager.getInstance(myProject).performLaterWhenAllCommitted(() -> {
        if (!myEditor.getComponent().isShowing()) return;
        Rectangle viewRect = myEditor.getScrollingModel().getVisibleArea();
        if (y < viewRect.y) {
          int start = lbraceStart;
          if (!(myPsiFile instanceof PsiPlainTextFile) && myPsiFile.isValid()) {
            start = BraceMatchingUtil.getBraceMatcher(getFileTypeByOffset(lbraceStart), PsiUtilCore
                    .getLanguageAtOffset(myPsiFile, lbraceStart)).getCodeConstructStart(myPsiFile, lbraceStart);
          }
          TextRange range = new TextRange(start, lbraceEnd);
          int line1 = myDocument.getLineNumber(range.getStartOffset());
          int line2 = myDocument.getLineNumber(range.getEndOffset());
          line1 = Math.max(line1, line2 - 5);
          range = new TextRange(myDocument.getLineStartOffset(line1), range.getEndOffset());
          LightweightHint hint = EditorFragmentComponent.showEditorFragmentHint(myEditor, range, true, true);
          myEditor.putUserData(HINT_IN_EDITOR_KEY, hint);
        }
      });
    }, 300, ModalityState.stateForComponent(myEditor.getComponent()));
  }

  void clearBraceHighlighters() {
    List<RangeHighlighter> highlighters = getHighlightersList();
    for (final RangeHighlighter highlighter : highlighters) {
      highlighter.dispose();
    }
    highlighters.clear();

    LightweightHint hint = myEditor.getUserData(HINT_IN_EDITOR_KEY);
    if (hint != null) {
      hint.hide();
      myEditor.putUserData(HINT_IN_EDITOR_KEY, null);
    }
  }

  private void lineMarkFragment(int startLine, int endLine, @Nonnull ColorValue color) {
    removeLineMarkers();

    if (startLine >= endLine || endLine >= myDocument.getLineCount()) return;

    int startOffset = myDocument.getLineStartOffset(startLine);
    int endOffset = myDocument.getLineStartOffset(endLine);

    RangeHighlighter highlighter = myEditor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, 0, null, HighlighterTargetArea.LINES_IN_RANGE);
    highlighter.setLineMarkerRenderer(new MyLineMarkerRenderer(color));
    myEditor.putUserData(LINE_MARKER_IN_EDITOR_KEY, highlighter);
  }

  private void removeLineMarkers() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    RangeHighlighter marker = myEditor.getUserData(LINE_MARKER_IN_EDITOR_KEY);
    if (marker != null && ((MarkupModelEx)myEditor.getMarkupModel()).containsHighlighter(marker)) {
      marker.dispose();
    }
    myEditor.putUserData(LINE_MARKER_IN_EDITOR_KEY, null);
  }

  private static class MyLineMarkerRenderer implements LineMarkerRenderer {
    private static final int DEEPNESS = 0;
    private static final int THICKNESS = 1;
    private final ColorValue myColor;

    private MyLineMarkerRenderer(@Nonnull ColorValue color) {
      myColor = color;
    }

    @Override
    public void paint(Editor editor, Graphics g, Rectangle r) {
      int height = r.height + editor.getLineHeight();
      g.setColor(TargetAWT.to(myColor));
      g.fillRect(r.x, r.y, THICKNESS, height);
      g.fillRect(r.x + THICKNESS, r.y, DEEPNESS, THICKNESS);
      g.fillRect(r.x + THICKNESS, r.y + height - THICKNESS, DEEPNESS, THICKNESS);
    }
  }
}
