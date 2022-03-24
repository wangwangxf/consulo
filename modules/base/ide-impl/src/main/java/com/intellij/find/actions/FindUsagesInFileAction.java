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

package com.intellij.find.actions;

import consulo.application.CommonBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.find.FindBundle;
import consulo.dataContext.DataContext;
import consulo.language.Language;
import consulo.language.findUsage.EmptyFindUsagesProvider;
import consulo.language.internal.LanguageFindUsages;
import com.intellij.openapi.actionSystem.*;
import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditor;
import consulo.language.editor.CommonDataKeys;
import consulo.project.Project;
import com.intellij.openapi.ui.Messages;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.ide.impl.psi.util.PsiUtilBase;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;

public class FindUsagesInFileAction extends AnAction {

  public FindUsagesInFileAction() {
    setInjectedContext(true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    Editor editor = dataContext.getData(PlatformDataKeys.EDITOR);

    UsageTarget[] usageTargets = dataContext.getData(UsageView.USAGE_TARGETS_KEY);
    if (usageTargets != null) {
      FileEditor fileEditor = dataContext.getData(PlatformDataKeys.FILE_EDITOR);
      if (fileEditor != null) {
        usageTargets[0].findUsagesInEditor(fileEditor);
      }
    }
    else if (editor == null) {
      Messages.showMessageDialog(
        project,
        FindBundle.message("find.no.usages.at.cursor.error"),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
    }
    else {
      HintManager.getInstance().showErrorHint(editor, FindBundle.message("find.no.usages.at.cursor.error"));
    }
  }

  @Override
  public void update(AnActionEvent event){
    updateFindUsagesAction(event);
  }

  private static boolean isEnabled(DataContext dataContext) {
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return false;
    }

    Editor editor = dataContext.getData(PlatformDataKeys.EDITOR);
    if (editor == null) {
      UsageTarget[] target = dataContext.getData(UsageView.USAGE_TARGETS_KEY);
      return target != null && target.length > 0;
    }
    else {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) {
        return false;
      }

      Language language = PsiUtilBase.getLanguageInEditor(editor, project);
      if (language == null) {
        language = file.getLanguage();
      }
      return !(LanguageFindUsages.INSTANCE.forLanguage(language) instanceof EmptyFindUsagesProvider);
    }
  }

  public static void updateFindUsagesAction(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    boolean enabled = isEnabled(dataContext);
    presentation.setVisible(enabled || !ActionPlaces.isPopupPlace(event.getPlace()));
    presentation.setEnabled(enabled);
  }
}
