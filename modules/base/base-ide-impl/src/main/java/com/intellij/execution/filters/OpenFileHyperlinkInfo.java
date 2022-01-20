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
package com.intellij.execution.filters;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class OpenFileHyperlinkInfo extends FileHyperlinkInfoBase {
  private final VirtualFile myVirtualFile;

  public OpenFileHyperlinkInfo(@Nonnull OpenFileDescriptor descriptor) {
    this(descriptor.getProject(), descriptor.getFile(), descriptor.getLine(), descriptor.getColumn());
  }

  public OpenFileHyperlinkInfo(@Nonnull Project project, @Nonnull final VirtualFile file, final int line) {
    this(project, file, line, 0);
  }

  public OpenFileHyperlinkInfo(@Nonnull Project project, @Nonnull VirtualFile file, int line, int column) {
    this(project, file, true, line, column);
  }

  public OpenFileHyperlinkInfo(@Nonnull Project project, @Nonnull VirtualFile file, boolean includeInOccurenceNavigation, int documentLine, int documentColumn) {
    super(project, includeInOccurenceNavigation, documentLine, documentColumn);
    myVirtualFile = file;
  }

  @Nullable
  @Override
  protected VirtualFile getVirtualFile() {
    return myVirtualFile;
  }
}