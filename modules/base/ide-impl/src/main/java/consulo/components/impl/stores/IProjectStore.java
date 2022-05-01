/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.components.impl.stores;

import consulo.project.internal.ProjectEx;
import consulo.ide.impl.idea.openapi.project.impl.ProjectImpl;
import consulo.component.store.impl.internal.IComponentStore;
import consulo.component.store.impl.internal.TrackingPathMacroSubstitutor;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @see ProjectEx#getStateStore()
 */
public interface IProjectStore extends IComponentStore {
  void setProjectFilePathNoUI(@Nonnull String filePath);

  void setProjectFilePath(@Nonnull String filePath);

  @Nullable
  VirtualFile getProjectBaseDir();

  @Nullable
  String getProjectBasePath();

  @Nonnull
  String getProjectName();

  TrackingPathMacroSubstitutor[] getSubstitutors();

  @Nullable
  String getPresentableUrl();

  @Nullable
  VirtualFile getProjectFile();

  @Nullable
  VirtualFile getWorkspaceFile();

  void loadProjectFromTemplate(@Nonnull ProjectImpl project);

  @Nonnull
  String getProjectFilePath();
}
