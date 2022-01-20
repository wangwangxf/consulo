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

package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import consulo.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import consulo.document.Document;
import com.intellij.openapi.editor.Editor;
import consulo.project.DumbAware;
import consulo.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

public class ReloadFromDiskAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = dataContext.getData(CommonDataKeys.PROJECT);
    final Editor editor = dataContext.getData(PlatformDataKeys.EDITOR);
    if (editor == null) return;
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) return;

    int res = Messages.showOkCancelDialog(
      project,
      IdeBundle.message("prompt.reload.file.from.disk", psiFile.getVirtualFile().getPresentableUrl()),
      IdeBundle.message("title.reload.file"),
      Messages.getWarningIcon()
    );
    if (res != 0) return;

    CommandProcessor.getInstance().executeCommand(
        project, new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              @Override
              public void run() {
                PsiManager.getInstance(project).reloadFromDisk(psiFile);
              }
            }
          );
        }
      },
        IdeBundle.message("command.reload.from.disk"),
        null
    );
  }

  @Override
  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null){
      presentation.setEnabled(false);
      return;
    }
    Editor editor = dataContext.getData(PlatformDataKeys.EDITOR);
    if (editor == null){
      presentation.setEnabled(false);
      return;
    }
    Document document = editor.getDocument();
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile == null || psiFile.getVirtualFile() == null){
      presentation.setEnabled(false);
    }
  }
}
