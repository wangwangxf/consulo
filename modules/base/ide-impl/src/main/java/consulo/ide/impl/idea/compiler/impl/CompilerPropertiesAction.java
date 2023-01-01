/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.ide.impl.idea.compiler.impl;

import consulo.application.AllIcons;
import consulo.compiler.CompilerBundle;
import consulo.ide.impl.compiler.setting.CompilerConfigurable;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/12/12
 */
public class CompilerPropertiesAction extends AnAction {
  public CompilerPropertiesAction() {
    super(CompilerBundle.message("action.compiler.properties.text"), null, AllIcons.General.Settings);
  }

  @RequiredUIAccess
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(Project.KEY);
    if (project != null) {
      ShowSettingsUtil.getInstance().showAndSelect(project, CompilerConfigurable.class);
    }
  }
}
