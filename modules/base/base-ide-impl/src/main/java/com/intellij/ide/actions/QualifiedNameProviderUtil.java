// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.actions;

import consulo.project.Project;
import com.intellij.psi.PsiElement;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QualifiedNameProviderUtil {
  private QualifiedNameProviderUtil() {
  }

  @Nullable
  public static PsiElement adjustElementToCopy(@Nonnull PsiElement element) {
    for (QualifiedNameProvider provider : QualifiedNameProvider.EP_NAME.getExtensionList()) {
      PsiElement adjustedElement = provider.adjustElementToCopy(element);
      if (adjustedElement != null) return adjustedElement;
    }
    return null;
  }

  @Nullable
  public static String getQualifiedName(@Nonnull PsiElement element) {
    for (QualifiedNameProvider provider : QualifiedNameProvider.EP_NAME.getExtensionList()) {
      String qualifiedName = provider.getQualifiedName(element);
      if (qualifiedName != null) return qualifiedName;
    }
    return null;
  }

  @Nullable
  public static PsiElement qualifiedNameToElement(@Nonnull String qualifiedName, @Nonnull Project project) {
    for (QualifiedNameProvider provider : QualifiedNameProvider.EP_NAME.getExtensionList()) {
      PsiElement element = provider.qualifiedNameToElement(qualifiedName, project);
      if (element != null) return element;
    }
    return null;
  }
}
