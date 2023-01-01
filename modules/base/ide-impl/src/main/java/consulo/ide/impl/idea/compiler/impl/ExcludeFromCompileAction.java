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

import consulo.compiler.CompilerBundle;
import consulo.compiler.CompilerManager;
import consulo.compiler.setting.ExcludeEntryDescription;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.status.FileStatusManager;

import javax.annotation.Nullable;

/**
* @author Eugene Zhuravlev
*         Date: 9/12/12
*/
public abstract class ExcludeFromCompileAction extends AnAction {
  private final Project myProject;

  public ExcludeFromCompileAction(Project project) {
    super(CompilerBundle.message("actions.exclude.from.compile.text"));
    myProject = project;
  }

  @RequiredUIAccess
  @Override
  public void actionPerformed(AnActionEvent e) {
    VirtualFile file = getFile();

    if (file != null && file.isValid()) {
      ExcludeEntryDescription description = new ExcludeEntryDescription(file, false, true, myProject);
      CompilerManager.getInstance(myProject).getExcludedEntriesConfiguration().addExcludeEntryDescription(description);
      FileStatusManager.getInstance(myProject).fileStatusesChanged();
    }
  }

  @Nullable
  protected abstract VirtualFile getFile();

  @RequiredUIAccess
  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final boolean isApplicable = getFile() != null;
    presentation.setEnabled(isApplicable);
    presentation.setVisible(isApplicable);
  }
}
