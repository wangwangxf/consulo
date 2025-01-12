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
package consulo.ide.impl.idea.ui.tabs.impl;

import consulo.ide.impl.idea.openapi.actionSystem.ex.ActionUtil;
import consulo.ide.impl.idea.openapi.keymap.KeymapUtil;
import consulo.ide.impl.idea.openapi.util.Comparing;
import consulo.ide.impl.idea.ui.InplaceButton;
import consulo.ui.ex.awt.tab.TabInfo;
import consulo.ui.ex.awt.util.TimedDeadzone;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.popup.IconButton;

import jakarta.annotation.Nullable;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

class ActionButton extends IconButton implements ActionListener {

  private final InplaceButton myButton;
  private Presentation myPrevPresentation;
  private final AnAction myAction;
  private final String myPlace;
  private final TabInfo myTabInfo;
  private final JBTabsImpl myTabs;
  private boolean myAutoHide;
  private boolean myToShow;

  public ActionButton(JBTabsImpl tabs, TabInfo tabInfo, AnAction action, String place, Consumer<MouseEvent> pass, TimedDeadzone.Length deadzone) {
    super(null, action.getTemplatePresentation().getIcon());
    myTabs = tabs;
    myTabInfo = tabInfo;
    myAction = action;
    myPlace = place;

    myButton = new InplaceButton(this, this, pass, deadzone) {
      @Override
      protected void doRepaintComponent(Component c) {
        repaintComponent(c);
      }
    };
    myButton.setVisible(false);
  }

  public InplaceButton getComponent() {
    return myButton;
  }

  protected void repaintComponent(Component c) {
    c.repaint();
  }

  public void setMouseDeadZone(TimedDeadzone.Length deadZone) {
    myButton.setMouseDeadzone(deadZone);
  }

  public boolean update() {
    AnActionEvent event = createAnEvent(null, 0);

    if (event == null) return false;

    myAction.update(event);
    Presentation p = event.getPresentation();
    boolean changed = !areEqual(p, myPrevPresentation);

    setIcons(p.getIcon(), p.getDisabledIcon(), p.getHoveredIcon());

    if (changed) {
      myButton.setIcons(this);
      String tooltipText = KeymapUtil.createTooltipText(p.getText(), myAction);
      myButton.setToolTipText(tooltipText.length() > 0 ? tooltipText : null);
      myButton.setVisible(p.isEnabled() && p.isVisible());
    }

    myPrevPresentation = p;

    return changed;
  }


  private static boolean areEqual(Presentation p1, Presentation p2) {
    if (p1 == null || p2 == null) return false;

    return Comparing.equal(p1.getText(), p2.getText())
           && Comparing.equal(p1.getIcon(), p2.getIcon())
           && Comparing.equal(p1.getHoveredIcon(), p2.getHoveredIcon())
           && p1.isEnabled() == p2.isEnabled()
           && p1.isVisible() == p2.isVisible();

  }

  @Override
  public void actionPerformed(final ActionEvent e) {
    AnActionEvent event = createAnEvent(null, e.getModifiers());
    if (event != null && ActionUtil.lastUpdateAndCheckDumb(myAction, event, true)) {
      ActionUtil.performActionDumbAware(myAction, event);
    }
  }

  @Nullable
  private AnActionEvent createAnEvent(InputEvent e, final int modifiers) {
    Presentation presentation = myAction.getTemplatePresentation().clone();
    DataContext context = DataManager.getInstance().getDataContext(myTabInfo.getComponent());
    return new AnActionEvent(e, context, myPlace != null ? myPlace : ActionPlaces.UNKNOWN, presentation, myTabs.myActionManager, modifiers);
  }

  public void setAutoHide(final boolean autoHide) {
    myAutoHide = autoHide;
    if (!myToShow) {
      toggleShowActions(false);
    }
  }

  public void toggleShowActions(boolean show) {
    if (myAutoHide) {
      myButton.setPainting(show);
    } else {
      myButton.setPainting(true);
    }

    myToShow = show;
  }
}
