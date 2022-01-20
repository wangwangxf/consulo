/*
 * Copyright 2013-2021 consulo.io
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
package com.intellij.ide.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import consulo.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import consulo.virtualFileSystem.VirtualFile;
import consulo.util.lang.ObjectUtil;

import javax.annotation.Nullable;

// from kotlin
public class CopyContentRootPathProvider extends DumbAwareCopyPathProvider {
  @Nullable
  @Override
  public String getPathToElement(Project project, @Nullable VirtualFile virtualFile, @Nullable Editor editor) {
    return ObjectUtil.doIfNotNull(virtualFile, file -> {
      Module moduleForFile = ProjectFileIndex.getInstance(project).getModuleForFile(file, false);
      if (moduleForFile != null) {
        VirtualFile[] contentRoots = ModuleRootManager.getInstance(moduleForFile).getContentRoots();

        for (VirtualFile contentRoot : contentRoots) {
          String relativePath = VfsUtilCore.getRelativePath(file, contentRoot);
          if (relativePath != null) {
            return relativePath;
          }
        }
      }
      return null;
    });
  }
}
