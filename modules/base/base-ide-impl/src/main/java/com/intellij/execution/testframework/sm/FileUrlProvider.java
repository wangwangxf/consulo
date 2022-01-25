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
package com.intellij.execution.testframework.sm;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import consulo.document.Document;
import consulo.language.psi.*;
import consulo.project.DumbAware;
import consulo.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.util.io.URLUtil;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class FileUrlProvider implements SMTestLocator, DumbAware {

  public static final FileUrlProvider INSTANCE = new FileUrlProvider();

  @Nonnull
  @Override
  public List<Location> getLocation(@Nonnull String protocol, @Nonnull String path, @Nonnull Project project, @Nonnull GlobalSearchScope scope) {
    if (!URLUtil.FILE_PROTOCOL.equals(protocol)) {
      return Collections.emptyList();
    }

    final String filePath;
    final int lineNumber;
    final int columnNumber;

    int lastColonIndex = path.lastIndexOf(':');
    if (lastColonIndex > 3) {   // on Windows, paths start with /C: and that colon is not a line number separator
      int lastValue = StringUtil.parseInt(path.substring(lastColonIndex + 1), -1);
      int penultimateColonIndex = path.lastIndexOf(':', lastColonIndex - 1);
      if (penultimateColonIndex > 3) {
        int penultimateValue = StringUtil.parseInt(path.substring(penultimateColonIndex + 1, lastColonIndex), -1);
        filePath = path.substring(0, penultimateColonIndex);
        lineNumber = penultimateValue;
        columnNumber = lineNumber <= 0 ? -1 : lastValue;
      }
      else {
        filePath = path.substring(0, lastColonIndex);
        lineNumber = lastValue;
        columnNumber = -1;
      }
    }
    else {
      filePath = path;
      lineNumber = -1;
      columnNumber = -1;
    }
    // Now we should search file with most suitable path
    // here path may be absolute or relative
    final String systemIndependentPath = FileUtil.toSystemIndependentName(filePath);
    final List<VirtualFile> virtualFiles = TestsLocationProviderUtil.findSuitableFilesFor(systemIndependentPath, project);
    if (virtualFiles.isEmpty()) {
      return Collections.emptyList();
    }

    final List<Location> locations = new ArrayList<>(2);
    for (VirtualFile file : virtualFiles) {
      locations.add(createLocationFor(project, file, lineNumber, columnNumber));
    }
    return locations;
  }

  @javax.annotation.Nullable
  public static Location createLocationFor(@Nonnull Project project, @Nonnull VirtualFile virtualFile, int lineNum) {
    return createLocationFor(project, virtualFile, lineNum, -1);
  }

  /**
   * @param project     Project instance
   * @param virtualFile VirtualFile instance to locate
   * @param lineNum     one-based line number to locate inside {@code virtualFile},
   *                    a non-positive line number doesn't change text caret position inside the file
   * @param columnNum   one-based column number to locate inside {@code virtualFile},
   *                    a non-positive column number doesn't change text caret position inside the file
   * @return Location instance, or null if not found
   */
  @javax.annotation.Nullable
  public static Location createLocationFor(@Nonnull Project project, @Nonnull VirtualFile virtualFile, int lineNum, int columnNum) {
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    if (psiFile == null) {
      return null;
    }
    if (lineNum <= 0) {
      return PsiLocation.fromPsiElement(psiFile);
    }

    final Document doc = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (doc == null) {
      return null;
    }

    if (lineNum > doc.getLineCount()) {
      return PsiLocation.fromPsiElement(psiFile);
    }

    final int lineStartOffset = doc.getLineStartOffset(lineNum - 1);
    final int endOffset = doc.getLineEndOffset(lineNum - 1);

    int offset = Math.min(lineStartOffset + Math.max(columnNum - 1, 0), endOffset);
    PsiElement elementAtLine = null;
    while (offset <= endOffset) {
      elementAtLine = psiFile.findElementAt(offset);
      if (!(elementAtLine instanceof PsiWhiteSpace)) break;
      int length = elementAtLine.getTextLength();
      offset += length > 1 ? length - 1 : 1;
    }

    return PsiLocation.fromPsiElement(project, elementAtLine != null ? elementAtLine : psiFile);
  }
}
