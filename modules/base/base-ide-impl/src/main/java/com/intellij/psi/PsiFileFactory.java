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
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.components.ServiceManager;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.annotation.DeprecationInfo;
import consulo.lang.LanguageVersion;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author max
 */
public abstract class PsiFileFactory {
  public static Key<PsiFile> ORIGINAL_FILE = Key.create("ORIGINAL_FILE");

  public static PsiFileFactory getInstance(Project project) {
    return ServiceManager.getService(project, PsiFileFactory.class);
  }

  /**
   * Please use {@link #createFileFromText(String, FileType, CharSequence)},
   * since file type detecting by file extension becomes vulnerable when file type mappings are changed.
   * <p/>
   * Creates a file from the specified text.
   *
   * @param name the name of the file to create (the extension of the name determines the file type).
   * @param text the text of the file to create.
   * @return the created file.
   * @throws com.intellij.util.IncorrectOperationException if the file type with specified extension is binary.
   */
  @Deprecated
  @Nonnull
  public abstract PsiFile createFileFromText(@Nonnull String name, @Nonnull String text);

  @Nonnull
  public abstract PsiFile createFileFromText(@Nonnull String fileName, @Nonnull FileType fileType, @Nonnull CharSequence text);

  @Nonnull
  public abstract PsiFile createFileFromText(@Nonnull String name, @Nonnull FileType fileType, @Nonnull CharSequence text, long modificationStamp, boolean physical);

  @Nonnull
  public abstract PsiFile createFileFromText(@Nonnull String name, @Nonnull FileType fileType, @Nonnull CharSequence text, long modificationStamp, boolean physical, boolean markAsCopy);

  public abstract PsiFile createFileFromText(@Nonnull String name, @Nonnull Language language, @Nonnull CharSequence text);

  public abstract PsiFile createFileFromText(@Nonnull String name, @Nonnull Language language, @Nonnull LanguageVersion languageVersion, @Nonnull CharSequence text);

  public abstract PsiFile createFileFromText(@Nonnull String name, @Nonnull Language language, @Nonnull CharSequence text, boolean physical, boolean markAsCopy);

  public abstract PsiFile createFileFromText(@Nonnull String name, @Nonnull Language language, @Nonnull CharSequence text, boolean physical, boolean markAsCopy, boolean noSizeLimit);

  @Deprecated
  @DeprecationInfo("Use #createFileFromText() without Language parameter")
  @Nullable
  public abstract PsiFile createFileFromText(@Nonnull String name,
                                             @Nonnull Language language,
                                             @Nonnull LanguageVersion languageVersion,
                                             @Nonnull CharSequence text,
                                             boolean physical,
                                             boolean markAsCopy,
                                             boolean noSizeLimit);

  @Nullable
  public PsiFile createFileFromText(@Nonnull String name, @Nonnull LanguageVersion languageVersion, @Nonnull CharSequence text, boolean physical, boolean markAsCopy, boolean noSizeLimit) {
    return createFileFromText(name, languageVersion, text, physical, markAsCopy, noSizeLimit, null);
  }

  @Nullable
  public abstract PsiFile createFileFromText(@Nonnull String name,
                                             @Nonnull LanguageVersion languageVersion,
                                             @Nonnull CharSequence text,
                                             boolean physical,
                                             boolean markAsCopy,
                                             boolean noSizeLimit,
                                             @Nullable VirtualFile original);

  public abstract PsiFile createFileFromText(FileType fileType, String fileName, CharSequence chars, int startOffset, int endOffset);

  @Nullable
  public abstract PsiFile createFileFromText(@Nonnull CharSequence chars, @Nonnull PsiFile original);
}