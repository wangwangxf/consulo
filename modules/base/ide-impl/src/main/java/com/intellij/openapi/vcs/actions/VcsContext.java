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
package com.intellij.openapi.vcs.actions;

import consulo.editor.Editor;
import consulo.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.ui.Refreshable;
import consulo.virtualFileSystem.VirtualFile;
import consulo.ui.ex.PlaceProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

public interface VcsContext extends PlaceProvider<String> {
  @javax.annotation.Nullable
  Project getProject();

  @Nullable
  VirtualFile getSelectedFile();

  @Nonnull
  VirtualFile[] getSelectedFiles();

  @Nonnull
  default Stream<VirtualFile> getSelectedFilesStream() {
    return Arrays.stream(getSelectedFiles());
  }

  Editor getEditor();

  Collection<VirtualFile> getSelectedFilesCollection();

  File[] getSelectedIOFiles();

  int getModifiers();

  Refreshable getRefreshableDialog();

  File getSelectedIOFile();

  @Nonnull
  FilePath[] getSelectedFilePaths();

  @Nonnull
  default Stream<FilePath> getSelectedFilePathsStream() {
    return Arrays.stream(getSelectedFilePaths());
  }

  @javax.annotation.Nullable
  FilePath getSelectedFilePath();

  @javax.annotation.Nullable
  ChangeList[] getSelectedChangeLists();

  @javax.annotation.Nullable
  Change[] getSelectedChanges();

  String getActionName();
}
