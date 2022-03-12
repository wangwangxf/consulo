// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import consulo.language.codeStyle.CodeStyle;
import consulo.language.codeStyle.LanguageFormattingRestriction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.codeStyle.CodeStyleSettings;
import javax.annotation.Nonnull;

public class ExcludedFileFormattingRestriction implements LanguageFormattingRestriction {
  @Override
  public boolean isFormatterAllowed(@Nonnull PsiElement context) {
    try {
      PsiFile file = context.getContainingFile();
      CodeStyleSettings settings = CodeStyle.getSettings(file);
      return !settings.getExcludedFiles().contains(file);
    }
    catch (PsiInvalidElementAccessException e) {
      return false;
    }
  }
}
