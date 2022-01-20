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

/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.actions.MoveModulesOutsideGroupAction;
import com.intellij.ide.projectView.actions.MoveModulesToSubGroupAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import consulo.project.Project;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.*;

public class MoveModuleToGroupTopLevel extends ActionGroup {
  @Override
  public void update(AnActionEvent e){
    final DataContext dataContext = e.getDataContext();
    final Project project = dataContext.getData(CommonDataKeys.PROJECT);
    final Module[] modules = dataContext.getData(LangDataKeys.MODULE_CONTEXT_ARRAY);
    boolean active = project != null && modules != null && modules.length != 0;
    e.getPresentation().setVisible(active);
  }

  @Override
  @Nonnull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) {
      return EMPTY_ARRAY;
    }
    List<String> topLevelGroupNames = new ArrayList<String> (getTopLevelGroupNames(e.getDataContext()));
    Collections.sort ( topLevelGroupNames );

    List<AnAction> result = new ArrayList<AnAction>();
    result.add(new MoveModulesOutsideGroupAction());
    result.add(new MoveModulesToSubGroupAction(null));
    result.add(AnSeparator.getInstance());
    for (String name : topLevelGroupNames) {
      result.add(new MoveModuleToGroup(new ModuleGroup(new String[]{name})));
    }
    return result.toArray(new AnAction[result.size()]);
  }

  private static Collection<String> getTopLevelGroupNames(final DataContext dataContext) {
    final Project project = dataContext.getData(CommonDataKeys.PROJECT);

    final ModifiableModuleModel model = dataContext.getData(LangDataKeys.MODIFIABLE_MODULE_MODEL);

    Module[] allModules;
    if ( model != null ) {
      allModules = model.getModules();
    } else {
      allModules = ModuleManager.getInstance(project).getModules();
    }

    Set<String> topLevelGroupNames = new HashSet<String>();
    for (final Module child : allModules) {
      String[] group;
      if ( model != null ) {
        group = model.getModuleGroupPath(child);
      } else {
        group = ModuleManager.getInstance(project).getModuleGroupPath(child);
      }
      if (group != null) {
        topLevelGroupNames.add(group[0]);
      }
    }
    return topLevelGroupNames;
  }
}
