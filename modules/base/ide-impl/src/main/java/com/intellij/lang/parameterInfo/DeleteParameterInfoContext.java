/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.parameterInfo;

import consulo.editor.Editor;
import consulo.language.psi.PsiElement;
import consulo.util.dataholder.UserDataHolderEx;

public interface DeleteParameterInfoContext {
  PsiElement getParameterOwner();

  Editor getEditor();

  UserDataHolderEx getCustomContext();
}
