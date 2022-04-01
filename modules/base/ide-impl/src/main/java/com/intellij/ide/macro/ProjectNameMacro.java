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

package com.intellij.ide.macro;

import consulo.ide.IdeBundle;
import consulo.language.editor.CommonDataKeys;
import consulo.dataContext.DataContext;
import consulo.project.Project;

public final class ProjectNameMacro extends Macro {
  @Override
  public String getName() {
    return "ProjectName";
  }

  @Override
  public String getDescription() {
    return IdeBundle.message("macro.project.file.name");
  }

  @Override
  public String expand(DataContext dataContext) {
    final Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return null;
    }
    return project.getName();
  }
}