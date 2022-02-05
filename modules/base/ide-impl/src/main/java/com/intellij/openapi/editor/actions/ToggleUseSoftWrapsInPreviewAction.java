/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import consulo.ui.ex.action.AnActionEvent;
import consulo.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;

public class ToggleUseSoftWrapsInPreviewAction extends AbstractToggleUseSoftWrapsAction {
  public ToggleUseSoftWrapsInPreviewAction() {
    super(SoftWrapAppliancePlaces.PREVIEW, true);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    Editor editor = getEditor(e);
    return editor == null
           ? EditorSettingsExternalizable.getInstance().isUseSoftWraps(SoftWrapAppliancePlaces.PREVIEW)
           : editor.getSettings().isUseSoftWraps();
  }
}
