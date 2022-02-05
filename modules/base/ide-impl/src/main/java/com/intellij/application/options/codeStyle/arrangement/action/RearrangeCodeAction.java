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
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.codeInsight.actions.RearrangeCodeProcessor;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import consulo.document.Document;
import consulo.editor.Editor;
import consulo.editor.SelectionModel;
import consulo.project.Project;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import javax.annotation.Nonnull;
import consulo.ui.annotation.RequiredUIAccess;

/**
 * Arranges content at the target file(s).
 *
 * @author Denis Zhdanov
 * @since 8/30/12 10:01 AM
 */
public class RearrangeCodeAction extends AnAction {

  @RequiredUIAccess
  @Override
  public void update(@Nonnull AnActionEvent e) {
    PsiFile file = e.getDataContext().getData(CommonDataKeys.PSI_FILE);
    boolean enabled = file != null && Rearranger.EXTENSION.forLanguage(file.getLanguage()) != null;
    e.getPresentation().setEnabled(enabled);
  }

  @RequiredUIAccess
  @Override
  public void actionPerformed(@Nonnull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }

    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) {
      return;
    }

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = editor.getDocument();
    documentManager.commitDocument(document);

    final PsiFile file = documentManager.getPsiFile(document);
    if (file == null) {
      return;
    }

    SelectionModel model = editor.getSelectionModel();
    if (model.hasSelection()) {
      new RearrangeCodeProcessor(file, model).run();
    }
    else {
      new RearrangeCodeProcessor(file).run();
    }
  }
}
