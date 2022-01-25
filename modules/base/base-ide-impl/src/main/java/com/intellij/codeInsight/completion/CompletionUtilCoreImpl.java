package com.intellij.codeInsight.completion;

import com.intellij.injected.editor.DocumentWindow;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author yole
 */
public class CompletionUtilCoreImpl {
  @Nullable
  public static <T extends PsiElement> T getOriginalElement(@Nonnull T psi) {
    final PsiFile file = psi.getContainingFile();
    return getOriginalElement(psi, file);
  }

  public static <T extends PsiElement> T getOriginalElement(@Nonnull T psi, PsiFile containingFile) {
    if (containingFile != null && containingFile != containingFile.getOriginalFile() && psi.getTextRange() != null) {
      TextRange range = psi.getTextRange();
      Integer start = range.getStartOffset();
      Integer end = range.getEndOffset();
      final Document document = containingFile.getViewProvider().getDocument();
      if (document != null) {
        Document hostDocument = document instanceof DocumentWindow ? ((DocumentWindow)document).getDelegate() : document;
        OffsetTranslator translator = hostDocument.getUserData(OffsetTranslator.RANGE_TRANSLATION);
        if (translator != null) {
          if (document instanceof DocumentWindow) {
            TextRange translated = ((DocumentWindow)document).injectedToHost(new TextRange(start, end));
            start = translated.getStartOffset();
            end = translated.getEndOffset();
          }

          start = translator.translateOffset(start);
          end = translator.translateOffset(end);
          if (start == null || end == null) {
            return null;
          }

          if (document instanceof DocumentWindow) {
            start = ((DocumentWindow)document).hostToInjected(start);
            end = ((DocumentWindow)document).hostToInjected(end);
          }
        }
      }
      //noinspection unchecked
      return (T)PsiTreeUtil.findElementOfClassAtRange(containingFile.getOriginalFile(), start, end, psi.getClass());
    }

    return psi;
  }
}
