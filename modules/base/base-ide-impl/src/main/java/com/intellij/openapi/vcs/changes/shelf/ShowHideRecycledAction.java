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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.*;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;

public class ShowHideRecycledAction extends AnAction {
  @RequiredUIAccess
  @Override
  public void update(@Nonnull final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Presentation presentation = e.getPresentation();
    if (project == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    presentation.setEnabled(true);
    presentation.setVisible(true);
    final boolean show = ShelveChangesManager.getInstance(project).isShowRecycled();
    presentation.setText(show ? "Hide Already Unshelved" : "Show Already Unshelved");
  }

  @Override
  @RequiredUIAccess
  public void actionPerformed(@Nonnull final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    final ShelveChangesManager manager = ShelveChangesManager.getInstance(project);
    final boolean show = manager.isShowRecycled();
    manager.setShowRecycled(! show);
  }
}
