// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.daemon.impl.IdentifierUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.scratch.RootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import consulo.dataContext.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import consulo.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import consulo.editor.colorScheme.EditorColorsManager;
import consulo.editor.markup.TextAttributes;
import consulo.language.psi.*;
import consulo.module.Module;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import consulo.virtualFileSystem.VirtualFile;
import consulo.project.ui.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.util.containers.ContainerUtil;
import consulo.codeInsight.TargetElementUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CopyReferenceUtil {
  static void highlight(Editor editor, Project project, List<? extends PsiElement> elements) {
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

    HighlightManager highlightManager = HighlightManager.getInstance(project);
    if (elements.size() == 1 && editor != null && project != null) {
      PsiElement element = elements.get(0);
      PsiElement nameIdentifier = IdentifierUtil.getNameIdentifier(element);
      if (nameIdentifier != null) {
        highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{nameIdentifier}, attributes, true, null);
      }
      else {
        PsiReference reference = TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());
        if (reference != null) {
          highlightManager.addOccurrenceHighlights(editor, new PsiReference[]{reference}, attributes, true, null);
        }
        else if (element != PsiDocumentManager.getInstance(project).getCachedPsiFile(editor.getDocument())) {
          highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{element}, attributes, true, null);
        }
      }
    }
  }

  @Nonnull
  static List<PsiElement> getElementsToCopy(@Nullable final Editor editor, final DataContext dataContext) {
    List<PsiElement> elements = new ArrayList<>();
    if (editor != null) {
      PsiReference reference = TargetElementUtil.findReference(editor);
      if (reference != null) {
        ContainerUtil.addIfNotNull(elements, reference.getElement());
      }
    }

    if (elements.isEmpty()) {
      PsiElement[] psiElements = dataContext.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
      if (psiElements != null) {
        Collections.addAll(elements, psiElements);
      }
    }

    if (elements.isEmpty()) {
      ContainerUtil.addIfNotNull(elements, dataContext.getData(CommonDataKeys.PSI_ELEMENT));
    }

    if (elements.isEmpty() && editor == null) {
      final Project project = dataContext.getData(CommonDataKeys.PROJECT);
      VirtualFile[] files = dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
      if (project != null && files != null) {
        for (VirtualFile file : files) {
          ContainerUtil.addIfNotNull(elements, PsiManager.getInstance(project).findFile(file));
        }
      }
    }

    return ContainerUtil.mapNotNull(elements, element -> element instanceof PsiFile && !((PsiFile)element).getViewProvider().isPhysical() ? null : adjustElement(element));
  }

  static PsiElement adjustElement(PsiElement element) {
    PsiElement adjustedElement = QualifiedNameProviderUtil.adjustElementToCopy(element);
    return adjustedElement != null ? adjustedElement : element;
  }

  static void setStatusBarText(Project project, String message) {
    if (project != null) {
      final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(project);
      if (statusBar != null) {
        statusBar.setInfo(message);
      }
    }
  }

  @Nullable
  static String getQualifiedNameFromProviders(@Nullable PsiElement element) {
    if (element == null) return null;
    return DumbService.getInstance(element.getProject()).computeWithAlternativeResolveEnabled(() -> QualifiedNameProviderUtil.getQualifiedName(element));
  }

  static String doCopy(List<? extends PsiElement> elements, @Nullable Editor editor) {
    if (elements.isEmpty()) return null;

    List<String> fqns = new ArrayList<>();
    for (PsiElement element : elements) {
      String fqn = elementToFqn(element, editor);
      if (fqn == null) return null;

      fqns.add(fqn);
    }

    return StringUtil.join(fqns, "\n");
  }

  @Nullable
  static String elementToFqn(@Nullable final PsiElement element, @Nullable Editor editor) {
    String result = getQualifiedNameFromProviders(element);
    if (result != null) return result;

    if (editor != null) { //IDEA-70346
      PsiReference reference = TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());
      if (reference != null) {
        result = getQualifiedNameFromProviders(reference.resolve());
        if (result != null) return result;
      }
    }

    if (element instanceof PsiFile) {
      return FileUtil.toSystemIndependentName(getFileFqn((PsiFile)element));
    }
    if (element instanceof PsiDirectory) {
      return FileUtil.toSystemIndependentName(getVirtualFileFqn(((PsiDirectory)element).getVirtualFile(), element.getProject()));
    }

    return null;
  }

  @Nonnull
  static String getFileFqn(final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    return virtualFile == null ? file.getName() : getVirtualFileFqn(virtualFile, file.getProject());
  }

  @Nonnull
  public static String getVirtualFileFqn(@Nonnull VirtualFile virtualFile, @Nonnull Project project) {
    for (CopyReferenceAction.VirtualFileQualifiedNameProvider provider : CopyReferenceAction.VirtualFileQualifiedNameProvider.EP_NAME.getExtensionList()) {
      String qualifiedName = provider.getQualifiedName(project, virtualFile);
      if (qualifiedName != null) {
        return qualifiedName;
      }
    }

    Module module = ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile, false);
    if (module != null) {
      for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
        String relativePath = VfsUtilCore.getRelativePath(virtualFile, root);
        if (relativePath != null) {
          return relativePath;
        }
      }
    }

    VirtualFile dir = project.getBaseDir();
    if (dir == null) {
      return virtualFile.getPath();
    }
    String relativePath = VfsUtilCore.getRelativePath(virtualFile, dir);
    if (relativePath != null) {
      return relativePath;
    }

    RootType rootType = RootType.forFile(virtualFile);
    if (rootType != null) {
      VirtualFile scratchRootVirtualFile = VfsUtil.findFileByIoFile(new File(ScratchFileService.getInstance().getRootPath(rootType)), false);
      if (scratchRootVirtualFile != null) {
        String scratchRelativePath = VfsUtilCore.getRelativePath(virtualFile, scratchRootVirtualFile);
        if (scratchRelativePath != null) {
          return scratchRelativePath;
        }
      }
    }

    return virtualFile.getPath();
  }
}