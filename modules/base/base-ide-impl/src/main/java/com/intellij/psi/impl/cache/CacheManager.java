/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.psi.impl.cache;

import com.intellij.openapi.components.ServiceManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.application.util.function.Processor;
import javax.annotation.Nonnull;

public abstract class CacheManager {
  @Nonnull
  public static CacheManager getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, CacheManager.class);
  }

  @Nonnull
  public abstract PsiFile[] getFilesWithWord(@Nonnull String word, short occurenceMask, @Nonnull GlobalSearchScope scope, final boolean caseSensitively);

  @Nonnull
  public abstract VirtualFile[] getVirtualFilesWithWord(@Nonnull String word, short occurenceMask, @Nonnull GlobalSearchScope scope, final boolean caseSensitively);

  public abstract boolean processFilesWithWord(@Nonnull Processor<PsiFile> processor,
                                               @Nonnull String word,
                                               short occurenceMask,
                                               @Nonnull GlobalSearchScope scope,
                                               final boolean caseSensitively);
}

