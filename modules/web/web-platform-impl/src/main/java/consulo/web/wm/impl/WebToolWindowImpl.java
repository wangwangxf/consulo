/*
 * Copyright 2013-2017 consulo.io
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
package consulo.web.wm.impl;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowContentUiType;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.impl.DesktopInternalDecorator;
import com.intellij.ui.content.ContentManager;
import consulo.ui.Component;
import consulo.ui.RequiredUIAccess;
import consulo.ui.UIAccess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author VISTALL
 * @since 25-Sep-17
 */
public class WebToolWindowImpl implements ToolWindowEx {
  private final PropertyChangeSupport myChangeSupport;

  private ActionCallback myActivation;
  private ToolWindowFactory myContentFactory;
  private WebToolWindowManagerImpl myToolWindowManager;
  private String myId;
  private Icon myIcon;

  public WebToolWindowImpl(WebToolWindowManagerImpl toolWindowManager, String id, boolean canCloseContent, Component component) {
    myToolWindowManager = toolWindowManager;
    myId = id;
    myChangeSupport = new PropertyChangeSupport(this);
  }

  @Override
  public ActionCallback getReady(@NotNull Object requestor) {
    return null;
  }

  @Override
  public void addPropertyChangeListener(PropertyChangeListener l) {
    myChangeSupport.addPropertyChangeListener(l);
  }

  @Override
  public void removePropertyChangeListener(PropertyChangeListener l) {
    myChangeSupport.removePropertyChangeListener(l);
  }

  @Override
  public ToolWindowType getInternalType() {
    return null;
  }

  @Override
  public void stretchWidth(int value) {

  }

  @Override
  public void stretchHeight(int value) {

  }

  @Override
  public DesktopInternalDecorator getDecorator() {
    return null;
  }

  @Override
  public void setAdditionalGearActions(@Nullable ActionGroup additionalGearActions) {

  }

  @Override
  public void setTitleActions(AnAction... actions) {

  }

  @Override
  public void setUseLastFocusedOnActivation(boolean focus) {

  }

  @Override
  public boolean isUseLastFocusedOnActivation() {
    return false;
  }

  @NotNull
  @Override
  public String getId() {
    return myId;
  }

  @Override
  public boolean isActive() {
    return false;
  }

  @Override
  public void activate(@Nullable Runnable runnable) {

  }

  @Override
  public void activate(@Nullable Runnable runnable, boolean autoFocusContents) {

  }

  @Override
  public void activate(@Nullable Runnable runnable, boolean autoFocusContents, boolean forced) {

  }

  @Override
  public boolean isVisible() {
    return false;
  }

  @Override
  public void show(@Nullable Runnable runnable) {

  }

  @Override
  public void hide(@Nullable Runnable runnable) {

  }

  @Override
  public ToolWindowAnchor getAnchor() {
    return null;
  }

  @Override
  public void setAnchor(@NotNull ToolWindowAnchor anchor, @Nullable Runnable runnable) {

  }

  @RequiredUIAccess
  @Override
  public boolean isSplitMode() {
    UIAccess.assertIsUIThread();
    return myToolWindowManager.isSplitMode(myId);
  }

  @RequiredUIAccess
  @Override
  public void setSplitMode(boolean isSideTool, @Nullable Runnable runnable) {
    UIAccess.assertIsUIThread();
    myToolWindowManager.setSideTool(myId, isSideTool);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  @Override
  public boolean isAutoHide() {
    return false;
  }

  @Override
  public void setAutoHide(boolean state) {

  }

  @Override
  public ToolWindowType getType() {
    return null;
  }

  @Override
  public void setType(@NotNull ToolWindowType type, @Nullable Runnable runnable) {

  }

  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public void setIcon(Icon icon) {
    UIAccess.assertIsUIThread();
    myIcon = icon;
    final Icon oldIcon = getIcon();

    myIcon = icon;
    myChangeSupport.firePropertyChange(PROP_ICON, oldIcon, icon);
  }

  @Override
  public String getTitle() {
    return null;
  }

  @Override
  public void setTitle(String title) {

  }

  @NotNull
  @Override
  public String getStripeTitle() {
    return getId();
  }

  @Override
  public void setStripeTitle(@NotNull String title) {

  }

  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public void setAvailable(boolean available, @Nullable Runnable runnable) {

  }

  @Override
  public void setContentUiType(@NotNull ToolWindowContentUiType type, @Nullable Runnable runnable) {

  }

  @Override
  public void setDefaultContentUiType(@NotNull ToolWindowContentUiType type) {

  }

  @NotNull
  @Override
  public ToolWindowContentUiType getContentUiType() {
    return null;
  }

  @Override
  public void installWatcher(ContentManager contentManager) {

  }

  @Override
  public JComponent getComponent() {
    return null;
  }

  @Override
  public ContentManager getContentManager() {
    return null;
  }

  @Override
  public void setDefaultState(@Nullable ToolWindowAnchor anchor, @Nullable ToolWindowType type, @Nullable Rectangle floatingBounds) {

  }

  @Override
  public void setToHideOnEmptyContent(boolean hideOnEmpty) {

  }

  @Override
  public boolean isToHideOnEmptyContent() {
    return false;
  }

  @Override
  public void setShowStripeButton(boolean show) {

  }

  @Override
  public boolean isShowStripeButton() {
    return false;
  }

  @Override
  public boolean isDisposed() {
    return false;
  }

  @Override
  public void showContentPopup(InputEvent inputEvent) {

  }

  @Override
  public ActionCallback getActivation() {
    return null;
  }

  public ActionCallback setActivation(ActionCallback activation) {
    myActivation = activation;
    return myActivation;
  }

  public void ensureContentInitialized() {

  }

  public void setContentFactory(ToolWindowFactory contentFactory) {
    myContentFactory = contentFactory;
  }

  public ToolWindowFactory getContentFactory() {
    return myContentFactory;
  }
}
