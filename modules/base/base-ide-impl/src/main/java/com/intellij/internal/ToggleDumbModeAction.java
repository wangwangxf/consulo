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
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import consulo.application.progress.ProgressIndicator;
import consulo.project.DumbAware;
import consulo.project.DumbModeTask;
import com.intellij.openapi.project.DumbServiceImpl;
import consulo.project.Project;
import consulo.util.lang.TimeoutUtil;
import javax.annotation.Nonnull;

/**
 * @author peter
 */
public class ToggleDumbModeAction extends AnAction implements DumbAware {
  private volatile boolean myDumb = false;

  @Override
  public void actionPerformed(final AnActionEvent e) {
    if (myDumb) {
      myDumb = false;
    }
    else {
      myDumb = true;
      final Project project = e.getData(CommonDataKeys.PROJECT);
      if (project == null) return;

      DumbServiceImpl.getInstance(project).queueTask(new DumbModeTask() {
        @Override
        public void performInDumbMode(@Nonnull ProgressIndicator indicator) {
          while (myDumb) {
            indicator.checkCanceled();
            TimeoutUtil.sleep(100);
          }
        }
      });
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Project project = e.getData(CommonDataKeys.PROJECT);
    presentation.setEnabled(project != null && myDumb == DumbServiceImpl.getInstance(project).isDumb());
    if (myDumb) {
      presentation.setText("Exit Dumb Mode");
    }
    else {
      presentation.setText("Enter Dumb Mode");
    }
  }
}
