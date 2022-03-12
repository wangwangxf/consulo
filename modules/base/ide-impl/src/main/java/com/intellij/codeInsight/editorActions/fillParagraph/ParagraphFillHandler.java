package com.intellij.codeInsight.editorActions.fillParagraph;

import com.intellij.formatting.FormatterTagHandler;
import consulo.undoRedo.CommandProcessor;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.document.util.UnfairTextRange;
import consulo.util.lang.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import consulo.language.ast.IElementType;
import consulo.language.plain.psi.PsiPlainTextFile;
import consulo.language.psi.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;

/**
 * Defines general re-flow paragraph functionality.
 * Serves plain text files.
 *
 * User : ktisha
 */
public class ParagraphFillHandler {

  protected void performOnElement(@Nonnull final PsiElement element, @Nonnull final Editor editor) {
    final Document document = editor.getDocument();

    final TextRange textRange = getTextRange(element, editor);
    if (textRange.isEmpty()) return;
    final String text = textRange.substring(element.getContainingFile().getText());

    final List<String> subStrings = StringUtil.split(text, "\n", true);
    final String prefix = getPrefix(element);
    final String postfix = getPostfix(element);

    final StringBuilder stringBuilder = new StringBuilder();
    appendPrefix(element, text, stringBuilder);

    for (String string : subStrings) {
      final String startTrimmed = StringUtil.trimStart(string.trim(), prefix.trim());
      final String str = StringUtil.trimEnd(startTrimmed, postfix.trim());
      final String finalString = str.trim();
      if (!StringUtil.isEmptyOrSpaces(finalString))
        stringBuilder.append(finalString).append(" ");
    }
    appendPostfix(element, text, stringBuilder);

    final String replacementText = stringBuilder.toString();

    CommandProcessor.getInstance().executeCommand(element.getProject(), () -> {
      document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(),
                             replacementText);
      final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(
              CodeStyleSettingsManager.getSettings(element.getProject()), element.getLanguage());

      final PsiFile file = element.getContainingFile();
      FormatterTagHandler formatterTagHandler = new FormatterTagHandler(CodeStyleSettingsManager.getSettings(file.getProject()));
      List<TextRange> enabledRanges = formatterTagHandler.getEnabledRanges(file.getNode(), TextRange.create(0, document.getTextLength()));

      codeFormatter.doWrapLongLinesIfNecessary(editor, element.getProject(), document,
                                               textRange.getStartOffset(),
                                               textRange.getStartOffset() + replacementText.length() + 1,
                                               enabledRanges);
    }, null, document);

  }

  protected void appendPostfix(@Nonnull final PsiElement element,
                               @Nonnull final String text,
                               @Nonnull final StringBuilder stringBuilder) {
    final String postfix = getPostfix(element);
    if (text.endsWith(postfix.trim()))
      stringBuilder.append(postfix);
  }

  protected void appendPrefix(@Nonnull final PsiElement element,
                              @Nonnull final String text,
                              @Nonnull final StringBuilder stringBuilder) {
    final String prefix = getPrefix(element);
    if (text.startsWith(prefix.trim()))
      stringBuilder.append(prefix);
  }

  private TextRange getTextRange(@Nonnull final PsiElement element, @Nonnull final Editor editor) {
    int startOffset = getStartOffset(element, editor);
    int endOffset = getEndOffset(element, editor);
    return new UnfairTextRange(startOffset, endOffset);
  }

  private int getStartOffset(@Nonnull final PsiElement element, @Nonnull final Editor editor) {
    if (isBunchOfElement(element)) {
      final PsiElement firstElement = getFirstElement(element);
      return firstElement != null? firstElement.getTextRange().getStartOffset()
                                 : element.getTextRange().getStartOffset();
    }
    final int offset = editor.getCaretModel().getOffset();
    final int elementTextOffset = element.getTextOffset();
    final Document document = editor.getDocument();
    int lineNumber = document.getLineNumber(offset);

    while (lineNumber != document.getLineNumber(elementTextOffset)) {
      final String text = document.getText(TextRange.create(document.getLineStartOffset(lineNumber),
                                                            document.getLineEndOffset(lineNumber)));
      if (StringUtil.isEmptyOrSpaces(text)) {
        lineNumber += 1;
        break;
      }
      lineNumber -= 1;
    }
    final int lineStartOffset = lineNumber == document.getLineNumber(elementTextOffset) ? elementTextOffset : document.getLineStartOffset(lineNumber);
    final String lineText = document
            .getText(TextRange.create(lineStartOffset, document.getLineEndOffset(lineNumber)));
    int shift = StringUtil.findFirst(lineText, CharFilter.NOT_WHITESPACE_FILTER);

    return lineStartOffset + shift;
  }

  protected boolean isBunchOfElement(PsiElement element) {
    return element instanceof PsiComment;
  }

  private int getEndOffset(@Nonnull final PsiElement element, @Nonnull final Editor editor) {
    if (isBunchOfElement(element)) {
      final PsiElement next = getLastElement(element);
      return next != null? next.getTextRange().getEndOffset()
                         : element.getTextRange().getEndOffset();
    }
    final int offset = editor.getCaretModel().getOffset();
    final int elementTextOffset = element.getTextRange().getEndOffset();
    final Document document = editor.getDocument();
    int lineNumber = document.getLineNumber(offset);

    while (lineNumber != document.getLineNumber(elementTextOffset)) {
      final String text = document.getText(TextRange.create(document.getLineStartOffset(lineNumber),
                                                            document.getLineEndOffset(lineNumber)));
      if (StringUtil.isEmptyOrSpaces(text)) {
        lineNumber -= 1;
        break;
      }
      lineNumber += 1;
    }
    return document.getLineEndOffset(lineNumber);
  }

  @Nullable
  private PsiElement getFirstElement(@Nonnull final PsiElement element) {
    final IElementType elementType = element.getNode().getElementType();
    PsiElement prevSibling = element.getPrevSibling();
    PsiElement result = element;
    while (prevSibling != null && (prevSibling.getNode().getElementType().equals(elementType) ||
                                   (atWhitespaceToken(prevSibling) &&
                                    StringUtil.countChars(prevSibling.getText(), '\n') <= 1))) {
      String text = prevSibling.getText();
      final String prefix = getPrefix(element);
      final String postfix = getPostfix(element);
      text = StringUtil.trimStart(text.trim(), prefix.trim());
      text = StringUtil.trimEnd(text, postfix);

      if (prevSibling.getNode().getElementType().equals(elementType) &&
          StringUtil.isEmptyOrSpaces(text)) {
        break;
      }
      if (prevSibling.getNode().getElementType().equals(elementType))
        result = prevSibling;
      prevSibling = prevSibling.getPrevSibling();
    }
    return result;
  }

  @Nullable
  private PsiElement getLastElement(@Nonnull final PsiElement element) {
    final IElementType elementType = element.getNode().getElementType();
    PsiElement nextSibling = element.getNextSibling();
    PsiElement result = element;
    while (nextSibling != null && (nextSibling.getNode().getElementType().equals(elementType) ||
                                   (atWhitespaceToken(nextSibling) &&
                                    StringUtil.countChars(nextSibling.getText(), '\n') <= 1))) {
      String text = nextSibling.getText();
      final String prefix = getPrefix(element);
      final String postfix = getPostfix(element);
      text = StringUtil.trimStart(text.trim(), prefix.trim());
      text = StringUtil.trimEnd(text, postfix);

      if (nextSibling.getNode().getElementType().equals(elementType) &&
          StringUtil.isEmptyOrSpaces(text)) {
        break;
      }
      if (nextSibling.getNode().getElementType().equals(elementType))
        result = nextSibling;
      nextSibling = nextSibling.getNextSibling();
    }
    return result;
  }

  protected boolean atWhitespaceToken(@Nullable final PsiElement element) {
    return element instanceof PsiWhiteSpace;
  }

  protected boolean isAvailableForElement(@Nullable final PsiElement element) {
    return element != null;
  }

  protected boolean isAvailableForFile(@Nullable final PsiFile psiFile) {
    return psiFile instanceof PsiPlainTextFile;
  }

  @Nonnull
  protected String getPrefix(@Nonnull final PsiElement element) {
    return "";
  }

  @Nonnull
  protected String getPostfix(@Nonnull final PsiElement element) {
    return "";
  }

}
