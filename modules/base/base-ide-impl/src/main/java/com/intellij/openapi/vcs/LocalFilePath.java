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
package com.intellij.openapi.vcs;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.PathUtil;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.nio.charset.Charset;

public class LocalFilePath implements FilePath {
  @Nonnull
  private final String myPath;
  private final boolean myIsDirectory;

  public LocalFilePath(@Nonnull String path, boolean isDirectory) {
    myPath = FileUtil.toCanonicalPath(path);
    myIsDirectory = isDirectory;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LocalFilePath path = (LocalFilePath)o;

    if (myIsDirectory != path.myIsDirectory) return false;
    if (!FileUtil.PATH_HASHING_STRATEGY.equals(myPath, path.myPath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = FileUtil.PATH_HASHING_STRATEGY.hashCode(myPath);
    result = 31 * result + (myIsDirectory ? 1 : 0);
    return result;
  }

  @Override
  public void refresh() {
  }

  @Override
  public void hardRefresh() {
    LocalFileSystem.getInstance().refreshAndFindFileByPath(myPath);
  }

  @Nonnull
  @Override
  public String getPath() {
    return myPath;
  }

  @Override
  public boolean isDirectory() {
    return myIsDirectory;
  }

  @Override
  public boolean isUnder(@Nonnull FilePath parent, boolean strict) {
    return FileUtil.isAncestor(parent.getPath(), getPath(), strict);
  }

  @Override
  @Nullable
  public FilePath getParentPath() {
    String parent = PathUtil.getParentPath(myPath);
    return parent.isEmpty() ? null : new LocalFilePath(parent, true);
  }

  @Override
  @Nullable
  public VirtualFile getVirtualFile() {
    return LocalFileSystem.getInstance().findFileByPath(myPath);
  }

  @Override
  @Nullable
  public VirtualFile getVirtualFileParent() {
    FilePath parent = getParentPath();
    return parent != null ? parent.getVirtualFile() : null;
  }

  @Override
  @Nonnull
  public File getIOFile() {
    return new File(myPath);
  }

  @Nonnull
  @Override
  public String getName() {
    return PathUtil.getFileName(myPath);
  }

  @Nonnull
  @Override
  public String getPresentableUrl() {
    return FileUtil.toSystemDependentName(myPath);
  }

  @Override
  @javax.annotation.Nullable
  public Document getDocument() {
    VirtualFile file = getVirtualFile();
    if (file == null || file.getFileType().isBinary()) {
      return null;
    }
    return FileDocumentManager.getInstance().getDocument(file);
  }

  @Override
  @Nonnull
  public Charset getCharset() {
    return getCharset(null);
  }

  @Override
  @Nonnull
  public Charset getCharset(@Nullable Project project) {
    VirtualFile file = getVirtualFile();
    String path = myPath;
    while ((file == null || !file.isValid()) && !path.isEmpty()) {
      path = PathUtil.getParentPath(path);
      file = LocalFileSystem.getInstance().findFileByPath(path);
    }
    if (file != null) {
      return file.getCharset();
    }
    EncodingManager e = project == null ? EncodingManager.getInstance() : EncodingProjectManager.getInstance(project);
    return e.getDefaultCharset();
  }

  @Override
  @Nonnull
  public FileType getFileType() {
    VirtualFile file = getVirtualFile();
    FileTypeManager manager = FileTypeManager.getInstance();
    return file != null ? manager.getFileTypeByFile(file) : manager.getFileTypeByFileName(getName());
  }

  @Override
  @NonNls
  public String toString() {
    return myPath + (myIsDirectory ? "/" : "");
  }

  @Override
  public boolean isNonLocal() {
    return false;
  }
}
