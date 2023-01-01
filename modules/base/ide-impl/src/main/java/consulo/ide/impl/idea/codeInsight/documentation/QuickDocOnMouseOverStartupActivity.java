/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.ide.impl.idea.codeInsight.documentation;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.impl.EditorSettingsExternalizable;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import consulo.project.startup.PostStartupActivity;
import consulo.ui.UIAccess;

/**
 * @author Denis Zhdanov
 * @since 7/2/12 9:44 AM
 */
@ExtensionImpl
public class QuickDocOnMouseOverStartupActivity implements PostStartupActivity, DumbAware {

  @Override
  public void runActivity(Project project, UIAccess uiAccess) {
    if (EditorSettingsExternalizable.getInstance().isShowQuickDocOnMouseOverElement()) {
      ServiceManager.getService(QuickDocOnMouseOverManager.class).setEnabled(true);
    }
  }
}
