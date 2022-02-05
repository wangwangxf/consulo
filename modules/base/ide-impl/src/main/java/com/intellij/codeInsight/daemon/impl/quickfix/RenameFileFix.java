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

/*
 * @author ven
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import consulo.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import consulo.document.Document;
import consulo.editor.Editor;
import consulo.document.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import consulo.project.Project;
import com.intellij.openapi.ui.ex.MessagesEx;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;

import javax.annotation.Nonnull;
import java.io.IOException;

public class RenameFileFix implements IntentionAction, LocalQuickFix {
  private final String myNewFileName;

  /**
   * @param newFileName with extension
   */
  public RenameFileFix(String newFileName) {
    myNewFileName = newFileName;
  }

  @Override
  @Nonnull
  public String getText() {
    return CodeInsightBundle.message("rename.file.fix");
  }

  @Override
  @Nonnull
  public String getName() {
    return getText();
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return CodeInsightBundle.message("rename.file.fix");
  }

  @Override
  public void applyFix(@Nonnull final Project project, @Nonnull ProblemDescriptor descriptor) {
    final PsiFile file = descriptor.getPsiElement().getContainingFile();
    if (isAvailable(project, null, file)) {
      new WriteCommandAction(project) {
        @Override
        protected void run(Result result) throws Throwable {
          invoke(project, FileEditorManager.getInstance(project).getSelectedTextEditor(), file);
        }
      }.execute();
    }
  }

  @Override
  public final boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!file.isValid()) return false;
    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return false;
    final VirtualFile parent = vFile.getParent();
    if (parent == null) return false;
    final VirtualFile newVFile = parent.findChild(myNewFileName);
    return newVFile == null || newVFile.equals(vFile);
  }


  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    FileDocumentManager.getInstance().saveDocument(document);
    try {
      vFile.rename(file.getManager(), myNewFileName);
    }
    catch(IOException e){
      MessagesEx.error(project, e.getMessage()).showLater();
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}