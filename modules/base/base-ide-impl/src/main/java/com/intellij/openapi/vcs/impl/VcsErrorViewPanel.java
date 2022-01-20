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
package com.intellij.openapi.vcs.impl;

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import consulo.project.Project;

/**
 * @author yole
*/
class VcsErrorViewPanel extends NewErrorTreeViewPanel {
  public VcsErrorViewPanel(Project project) {
    super(project, null);
  }

  @Override
  protected boolean canHideWarningsOrInfos() {
    return false;
  }
}