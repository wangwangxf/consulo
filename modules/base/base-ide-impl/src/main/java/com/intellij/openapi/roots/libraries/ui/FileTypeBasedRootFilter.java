/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.libraries.ui;

import consulo.content.library.ui.RootDetector;
import consulo.virtualFileSystem.archive.ArchiveFileType;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.application.progress.ProgressIndicator;
import consulo.content.OrderRootType;
import com.intellij.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import consulo.application.util.function.Processor;
import javax.annotation.Nonnull;

/**
 * Implementation of {@link RootDetector} which detects a root by presence of files of some specified type under it
 *
 * @author nik
 */
public class FileTypeBasedRootFilter extends RootFilter {
  private final FileType myFileType;

  public FileTypeBasedRootFilter(OrderRootType rootType, boolean jarDirectory, @Nonnull FileType fileType,
                                 final String presentableRootTypeName) {
    super(rootType, jarDirectory, presentableRootTypeName);
    myFileType = fileType;
  }

  @Override
  public boolean isAccepted(@Nonnull VirtualFile rootCandidate, @Nonnull final ProgressIndicator progressIndicator) {
    if (isJarDirectory()) {
      if (!rootCandidate.isDirectory() || !rootCandidate.isInLocalFileSystem()) {
        return false;
      }
      for (VirtualFile child : rootCandidate.getChildren()) {
        if (!child.isDirectory() && child.getFileType() instanceof ArchiveFileType) {
          final VirtualFile archiveRoot = ArchiveVfsUtil.getArchiveRootForLocalFile(child);
          if (archiveRoot != null && containsFileOfType(archiveRoot, progressIndicator)) {
            return true;
          }
        }
      }
      return false;
    }
    else {
      return containsFileOfType(rootCandidate, progressIndicator);
    }
  }

  private boolean containsFileOfType(VirtualFile rootCandidate, final ProgressIndicator progressIndicator) {
    return !VfsUtil.processFilesRecursively(rootCandidate, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile virtualFile) {
        progressIndicator.checkCanceled();
        if (virtualFile.isDirectory()) {
          progressIndicator.setText2(virtualFile.getPath());
          return true;
        }
        return !isFileAccepted(virtualFile);
      }
    });
  }

  protected boolean isFileAccepted(VirtualFile virtualFile) {
    return virtualFile.getFileType().equals(myFileType);
  }
}
