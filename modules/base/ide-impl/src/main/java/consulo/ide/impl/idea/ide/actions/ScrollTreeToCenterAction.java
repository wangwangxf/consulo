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
package consulo.ide.impl.idea.ide.actions;

import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.UIExAWTDataKey;
import consulo.ui.ex.awt.tree.TreeUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class ScrollTreeToCenterAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Component component = e.getData(UIExAWTDataKey.CONTEXT_COMPONENT);
    if (component instanceof JTree) {
      JTree tree = (JTree)component;
      final int[] selection = tree.getSelectionRows();
       if (selection != null && selection.length > 0) {
        TreeUtil.showRowCentered(tree, selection [0], false);
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(UIExAWTDataKey.CONTEXT_COMPONENT) instanceof JTree);
  }
}
