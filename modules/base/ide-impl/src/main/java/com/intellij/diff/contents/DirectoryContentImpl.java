/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.contents;

import com.intellij.openapi.fileEditor.OpenFileDescriptorImpl;
import consulo.diff.content.DiffContentBase;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.navigation.Navigatable;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DirectoryContentImpl extends DiffContentBase implements DirectoryContent {
  @Nonnull
  private final VirtualFile myFile;
  @Nullable private final Project myProject;

  public DirectoryContentImpl(@Nullable Project project, @Nonnull VirtualFile file) {
    assert file.isValid() && file.isDirectory();
    myProject = project;
    myFile = file;
  }

  @javax.annotation.Nullable
  @Override
  public Navigatable getNavigatable() {
    if (myProject == null || myProject.isDefault() || !myFile.isValid()) return null;
    return new OpenFileDescriptorImpl(myProject, myFile);
  }

  @Nonnull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @javax.annotation.Nullable
  @Override
  public FileType getContentType() {
    return null;
  }
}
