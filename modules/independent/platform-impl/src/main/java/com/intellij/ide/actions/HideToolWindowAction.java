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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;

public class HideToolWindowAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }

    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    String id = toolWindowManager.getActiveToolWindowId();
    if (id == null) {
      id = toolWindowManager.getLastActiveToolWindowId();
    }
    toolWindowManager.getToolWindow(id).hide(null);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    String id = toolWindowManager.getActiveToolWindowId();
    if (id != null) {
      presentation.setEnabled(true);
      return;
    }

    id = toolWindowManager.getLastActiveToolWindowId();
    if (id == null) {
      presentation.setEnabled(false);
      return;
    }

    ToolWindow toolWindow = toolWindowManager.getToolWindow(id);
    presentation.setEnabled(toolWindow.isVisible());
  }
}