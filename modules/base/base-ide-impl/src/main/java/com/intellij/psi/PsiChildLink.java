/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiRefElementCreator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author peter
 */
public abstract class PsiChildLink<Parent extends PsiElement, Child extends PsiElement> implements PsiRefElementCreator<Parent, Child> {
  
  @Nullable
  public abstract Child findLinkedChild(@Nullable Parent parent);

  @Nonnull
  public final PsiElementRef<Child> createChildRef(@Nonnull Parent parent) {
    final Child existing = findLinkedChild(parent);
    if (existing != null) {
      return PsiElementRef.real(existing);
    }
    return PsiElementRef.imaginary(PsiElementRef.real(parent), this);
  }

  @Nonnull
  public final PsiElementRef<Child> createChildRef(@Nonnull PsiElementRef<? extends Parent> parentRef) {
    final Parent parent = parentRef.getPsiElement();
    if (parent != null) {
      final Child existing = findLinkedChild(parent);
      if (existing != null) {
        return PsiElementRef.real(existing);
      }
    }
    return PsiElementRef.imaginary(parentRef, this);
  }

}
