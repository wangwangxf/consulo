/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import consulo.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Collection;

public class CheckLevelHighlightInfoHolder extends HighlightInfoHolder {
  private final HighlightInfoHolder myHolder;
  private PsiElement myLevel;

  public CheckLevelHighlightInfoHolder(@Nonnull PsiFile file, @Nonnull HighlightInfoHolder holder) {
    super(file);
    myHolder = holder;
  }

  @Nonnull
  @Override
  public TextAttributesScheme getColorsScheme() {
    return myHolder.getColorsScheme();
  }

  @Nonnull
  @Override
  public PsiFile getContextFile() {
    return myHolder.getContextFile();
  }

  @Nonnull
  @Override
  public Project getProject() {
    return myHolder.getProject();
  }

  @Override
  public boolean hasErrorResults() {
    return myHolder.hasErrorResults();
  }

  @Override
  public boolean add(@Nullable HighlightInfo info) {
    if (info == null) return false;
    PsiElement psiElement = info.psiElement;
    if (psiElement != null && !PsiTreeUtil.isAncestor(myLevel, psiElement, false)) {
      throw new RuntimeException("Info: '" + info + "' reported for the element '" + psiElement + "'; but it was at the level " + myLevel);
    }
    return myHolder.add(info);
  }

  @Override
  public void clear() {
    myHolder.clear();
  }

  @Override
  public boolean addAll(@Nonnull Collection<? extends HighlightInfo> highlightInfos) {
    return myHolder.addAll(highlightInfos);
  }

  @Override
  public int size() {
    return myHolder.size();
  }

  @Nonnull
  @Override
  public HighlightInfo get(int i) {
    return myHolder.get(i);
  }

  @Nonnull
  @Override
  public AnnotationSession getAnnotationSession() {
    return myHolder.getAnnotationSession();
  }

  public void enterLevel(PsiElement element) {
    myLevel = element;
  }
}
