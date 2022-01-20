// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import consulo.document.Document;
import com.intellij.openapi.editor.Editor;
import consulo.component.extension.ExtensionPointName;
import consulo.document.FileDocumentManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.Collections;
import java.util.List;

import static com.intellij.ide.actions.CopyReferenceUtil.*;

/**
 * @author Alexey
 */
public class CopyReferenceAction extends DumbAwareAction {
  public static final DataFlavor ourFlavor = FileCopyPasteUtil.createJvmDataFlavor(CopyReferenceFQNTransferable.class);

  public CopyReferenceAction() {
    super();
    setEnabledInModalContext(true);
    setInjectedContext(true);
  }

  @Override
  public void update(@Nonnull AnActionEvent e) {
    boolean plural = false;
    boolean enabled;
    boolean paths = false;

    DataContext dataContext = e.getDataContext();
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    if (editor != null && FileDocumentManager.getInstance().getFile(editor.getDocument()) != null) {
      enabled = true;
    }
    else {
      List<PsiElement> elements = getPsiElements(dataContext, editor);
      enabled = !elements.isEmpty();
      plural = elements.size() > 1;
      paths = elements.stream().allMatch(el -> el instanceof PsiFileSystemItem && getQualifiedNameFromProviders(el) == null);
    }

    e.getPresentation().setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(enabled);
    }
    else {
      e.getPresentation().setVisible(true);
    }
    e.getPresentation().setText(
            paths ? plural ? IdeBundle.message("copy.relative.paths") : IdeBundle.message("copy.relative.path") : plural ? IdeBundle.message("copy.references") : IdeBundle.message("copy.reference"));

    if (paths) {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  @Nonnull
  protected List<PsiElement> getPsiElements(DataContext dataContext, Editor editor) {
    return getElementsToCopy(editor, dataContext);
  }

  @RequiredUIAccess
  @Override
  public void actionPerformed(@Nonnull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    List<PsiElement> elements = getPsiElements(dataContext, editor);

    String copy = getQualifiedName(editor, elements);
    if (copy != null) {
      CopyPasteManager.getInstance().setContents(new CopyReferenceFQNTransferable(copy));
      setStatusBarText(project, IdeBundle.message("message.reference.to.fqn.has.been.copied", copy));
    }
    else if (editor != null && project != null) {
      Document document = editor.getDocument();
      PsiFile file = PsiDocumentManager.getInstance(project).getCachedPsiFile(document);
      if (file != null) {
        String toCopy = getFileFqn(file) + ":" + (editor.getCaretModel().getLogicalPosition().line + 1);
        CopyPasteManager.getInstance().setContents(new StringSelection(toCopy));
        setStatusBarText(project, LangBundle.message("status.bar.text.reference.has.been.copied", toCopy));
      }
      return;
    }

    highlight(editor, project, elements);
  }

  protected String getQualifiedName(Editor editor, List<? extends PsiElement> elements) {
    return CopyReferenceUtil.doCopy(elements, editor);
  }

  public static boolean doCopy(final PsiElement element, final Project project) {
    return doCopy(Collections.singletonList(element), project);
  }

  private static boolean doCopy(List<? extends PsiElement> elements, @Nullable final Project project) {
    String toCopy = CopyReferenceUtil.doCopy(elements, null);
    CopyPasteManager.getInstance().setContents(new CopyReferenceFQNTransferable(toCopy));
    setStatusBarText(project, IdeBundle.message("message.reference.to.fqn.has.been.copied", toCopy));

    return true;
  }

  @Nullable
  public static String elementToFqn(@Nullable final PsiElement element) {
    return CopyReferenceUtil.elementToFqn(element, null);
  }

  public interface VirtualFileQualifiedNameProvider {
    ExtensionPointName<VirtualFileQualifiedNameProvider> EP_NAME = ExtensionPointName.create("consulo.virtualFileQualifiedNameProvider");

    /**
     * @return {@code virtualFile} fqn (relative path for example) or null if not handled by this provider
     */
    @Nullable
    String getQualifiedName(@Nonnull Project project, @Nonnull VirtualFile virtualFile);
  }
}
