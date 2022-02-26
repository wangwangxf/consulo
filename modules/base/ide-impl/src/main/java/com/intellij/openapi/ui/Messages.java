/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import consulo.application.CommonBundle;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import consulo.project.Project;
import consulo.application.util.function.Computable;
import com.intellij.openapi.util.Pair;
import consulo.application.util.SystemInfo;
import consulo.document.util.TextRange;
import consulo.application.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import consulo.project.ui.wm.IdeFrame;
import consulo.project.ui.wm.WindowManager;
import com.intellij.ui.*;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBScrollPane;
import com.intellij.ui.mac.MacMessages;
import com.intellij.ui.mac.foundation.MacUtil;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.util.Alarm;
import com.intellij.util.PairFunction;
import consulo.process.cmd.ParametersListUtil;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.logging.Logger;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.image.Image;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.JTextComponent;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class Messages {
  public static final int OK = 0;
  public static final int YES = 0;
  public static final int NO = 1;
  public static final int CANCEL = 2;

  public static final String OK_BUTTON = CommonBundle.getOkButtonText();
  public static final String YES_BUTTON = CommonBundle.getYesButtonText();
  public static final String NO_BUTTON = CommonBundle.getNoButtonText();
  public static final String CANCEL_BUTTON = CommonBundle.getCancelButtonText();

  private static TestDialog ourTestImplementation = TestDialog.DEFAULT;
  private static TestInputDialog ourTestInputImplementation = TestInputDialog.DEFAULT;

  private static final Logger LOG = Logger.getInstance(Messages.class);

  @TestOnly
  public static TestDialog setTestDialog(TestDialog newValue) {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      LOG.assertTrue(application.isUnitTestMode(), "This method is available for tests only");
    }
    TestDialog oldValue = ourTestImplementation;
    ourTestImplementation = newValue;
    return oldValue;
  }

  @TestOnly
  public static TestInputDialog setTestInputDialog(TestInputDialog newValue) {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      LOG.assertTrue(application.isUnitTestMode(), "This method is available for tests only");
    }
    TestInputDialog oldValue = ourTestInputImplementation;
    ourTestInputImplementation = newValue;
    return oldValue;
  }

  @Nonnull
  public static Image getErrorIcon() {
    return UIUtil.getErrorIcon();
  }

  @Nonnull
  public static Image getInformationIcon() {
    return UIUtil.getInformationIcon();
  }

  @Nonnull
  public static Image getWarningIcon() {
    return UIUtil.getWarningIcon();
  }

  @Nonnull
  public static Image getQuestionIcon() {
    return UIUtil.getQuestionIcon();
  }

  @Nonnull
  public static Runnable createMessageDialogRemover(@Nullable Project project) {
    consulo.ui.Window projectWindow = project == null ? null : WindowManager.getInstance().suggestParentWindow(project);
    return () -> UIUtil.invokeLaterIfNeeded(() -> makeCurrentMessageDialogGoAway(projectWindow != null ? TargetAWT.to(projectWindow).getOwnedWindows() : Window.getWindows()));
  }

  private static void makeCurrentMessageDialogGoAway(@Nonnull Window[] checkWindows) {
    for (Window w : checkWindows) {
      JDialog dialog = w instanceof JDialog ? (JDialog)w : null;
      if (dialog == null || !dialog.isModal()) continue;
      JButton cancelButton = UIUtil.uiTraverser(dialog.getRootPane()).filter(JButton.class).filter(b -> CommonBundle.getCancelButtonText().equals(b.getText())).first();
      if (cancelButton != null) cancelButton.doClick();
    }
  }

  /**
   * Please, use {@link #showOkCancelDialog} or {@link #showYesNoCancelDialog} if possible (these dialogs implements native OS behavior)!
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(@Nullable Project project, String message, String title, @Nonnull String[] options, int defaultOptionIndex, @Nullable Image icon) {
    return showDialog(project, message, title, options, defaultOptionIndex, icon, null);
  }

  /**
   * Please, use {@link #showOkCancelDialog} or {@link #showYesNoCancelDialog} if possible (these dialogs implements native OS behavior)!
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(@Nullable Project project,
                               String message,
                               @Nonnull String title,
                               @Nonnull String[] options,
                               int defaultOptionIndex,
                               @Nullable Image icon,
                               @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }

    try {
      if (canShowMacSheetPanel()) {
        consulo.ui.Window parentWindow = WindowManager.getInstance().suggestParentWindow(project);
        return MacMessages.getInstance().showMessageDialog(title, message, options, false, parentWindow, defaultOptionIndex, defaultOptionIndex, doNotAskOption);
      }
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    return showIdeaMessageDialog(project, message, title, options, defaultOptionIndex, icon, doNotAskOption);
  }

  /**
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showIdeaMessageDialog(@Nullable Project project,
                                          String message,
                                          String title,
                                          @Nonnull String[] options,
                                          int defaultOptionIndex,
                                          @Nullable Image icon,
                                          @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    MessageDialog dialog = new MessageDialog(project, message, title, options, defaultOptionIndex, -1, icon, doNotAskOption, false);
    dialog.show();
    return dialog.getExitCode();
  }

  public static boolean canShowMacSheetPanel() {
    return SystemInfo.isMac && !isApplicationInUnitTestOrHeadless() && Registry.is("ide.mac.message.dialogs.as.sheets");
  }

  public static boolean isMacSheetEmulation() {
    return false;
  }

  /**
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(Project project,
                               String message,
                               @Nonnull String title,
                               @Nullable String moreInfo,
                               @Nonnull String[] options,
                               int defaultOptionIndex,
                               int focusedOptionIndex,
                               Image icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }

    try {
      if (canShowMacSheetPanel() && moreInfo == null) {
        return MacMessages.getInstance().showMessageDialog(title, message, options, false, WindowManager.getInstance().suggestParentWindow(project), defaultOptionIndex, focusedOptionIndex, null);
      }
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    MessageDialog dialog = new MoreInfoMessageDialog(project, message, title, moreInfo, options, defaultOptionIndex, focusedOptionIndex, icon);
    dialog.show();
    return dialog.getExitCode();
  }

  static boolean isApplicationInUnitTestOrHeadless() {
    final Application application = ApplicationManager.getApplication();
    return application != null && (application.isUnitTestMode() || application.isHeadlessEnvironment());
  }

  /**
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(@Nonnull Component parent, String message, @Nonnull String title, @Nonnull String[] options, int defaultOptionIndex, @Nullable Image icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      try {
        if (canShowMacSheetPanel()) {
          return MacMessages.getInstance().showMessageDialog(title, message, options, false, TargetAWT.from(SwingUtilities.getWindowAncestor(parent)), defaultOptionIndex, defaultOptionIndex, null);
        }
      }
      catch (Exception exception) {
        LOG.error(exception);
      }

      MessageDialog dialog = new MessageDialog(parent, message, title, options, defaultOptionIndex, defaultOptionIndex, icon, false);
      dialog.show();
      return dialog.getExitCode();
    }
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   * @see #showDialog(Project, String, String, String[], int, Image, DialogWrapper.DoNotAskOption)
   * @see #showDialog(Component, String, String, String[], int, Image)
   */
  public static int showDialog(String message,
                               @Nonnull String title,
                               @Nonnull String[] options,
                               int defaultOptionIndex,
                               int focusedOptionIndex,
                               @Nullable Image icon,
                               @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showMessageDialog(title, message, options, false, null, defaultOptionIndex, focusedOptionIndex, doNotAskOption);
      }
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    //what's it? if (application.isUnitTestMode()) throw new RuntimeException(message);
    MessageDialog dialog = new MessageDialog(message, title, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption);
    dialog.show();
    return dialog.getExitCode();
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   * @see #showDialog(Project, String, String, String[], int, Image)
   * @see #showDialog(Component, String, String, String[], int, Image)
   */
  public static int showDialog(String message, String title, @Nonnull String[] options, int defaultOptionIndex, @Nullable Image icon, @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    return showDialog(message, title, options, defaultOptionIndex, defaultOptionIndex, icon, doNotAskOption);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   * @see #showDialog(Project, String, String, String[], int, Image)
   * @see #showDialog(Component, String, String, String[], int, Image)
   */
  public static int showDialog(String message, String title, @Nonnull String[] options, int defaultOptionIndex, @Nullable Image icon) {
    return showDialog(message, title, options, defaultOptionIndex, icon, null);
  }

  /**
   * @see com.intellij.openapi.ui.DialogWrapper#DialogWrapper(Project, boolean)
   */
  public static void showMessageDialog(@Nullable Project project, String message, @Nonnull String title, @Nullable Image icon) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  public static void showMessageDialog(@Nonnull Component parent, String message, @Nonnull String title, @Nullable Image icon) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, TargetAWT.from(SwingUtilities.getWindowAncestor(parent)));
        return;
      }
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    showDialog(parent, message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showMessageDialog(Project, String, String, Image)
   * @see #showMessageDialog(Component, String, String, Image)
   */
  public static void showMessageDialog(String message, @Nonnull String title, @Nullable Image icon) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON);
        return;
      }
    }
    catch (Exception exception) {
      LOG.error(exception);
    }
    showDialog(message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  @MagicConstant(intValues = {YES, NO})
  public @interface YesNoResult {
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project, String message, @Nonnull String title, @Nonnull String yesText, @Nonnull String noText, @Nullable Image icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, yesText, noText, WindowManager.getInstance().suggestParentWindow(project));
      }
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    int result = showDialog(project, message, title, new String[]{yesText, noText}, 0, icon) == 0 ? YES : NO;
    //noinspection ConstantConditions
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project, String message, @Nonnull String title, @Nullable Image icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, YES_BUTTON, NO_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
      }
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    int result = showYesNoDialog(project, message, title, YES_BUTTON, NO_BUTTON, icon);

    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }


  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@Nonnull Component parent, String message, @Nonnull String title, @Nullable Image icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, YES_BUTTON, NO_BUTTON, TargetAWT.from(SwingUtilities.getWindowAncestor(parent)));
      }
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    int result = showDialog(parent, message, title, new String[]{YES_BUTTON, NO_BUTTON}, 0, icon) == 0 ? YES : NO;
    //noinspection ConstantConditions
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   * @see #showYesNoDialog(Project, String, String, javax.swing.Image)
   * @see #showYesNoCancelDialog(java.awt.Component, String, String, javax.swing.Image)
   */
  @YesNoResult
  public static int showYesNoDialog(String message,
                                    @Nonnull String title,
                                    @Nonnull String yesText,
                                    @Nonnull String noText,
                                    @Nullable Image icon,
                                    @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, yesText, noText, null, doNotAskOption);
      }
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    int result = showDialog(message, title, new String[]{yesText, noText}, 0, icon, doNotAskOption) == 0 ? YES : NO;
    //noinspection ConstantConditions
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   * @see #showYesNoDialog(Project, String, String, String, String, Image)
   * @see #showYesNoDialog(java.awt.Component, String, String, javax.swing.Image)
   */
  @YesNoResult
  public static int showYesNoDialog(String message, String title, String yesText, String noText, @Nullable Image icon) {
    return showYesNoDialog(message, title, yesText, noText, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   * @see #showYesNoDialog(Project, String, String, Image)
   * @see #showYesNoDialog(Component, String, String, Image)
   */
  @YesNoResult
  public static int showYesNoDialog(String message, @Nonnull String title, @Nullable Image icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, YES_BUTTON, NO_BUTTON, null);
      }
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    int result = showYesNoDialog(message, title, YES_BUTTON, NO_BUTTON, icon);
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  @MagicConstant(intValues = {OK, CANCEL})
  public @interface OkCancelResult {
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(Project project,
                                       String message,
                                       @Nonnull String title,
                                       @Nonnull String okText,
                                       @Nonnull String cancelText,
                                       Image icon,
                                       DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        int result = MacMessages.getInstance().showYesNoDialog(title, message, okText, cancelText, WindowManager.getInstance().suggestParentWindow(project), doNotAskOption);
        return result == YES ? OK : CANCEL;
      }
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    return showDialog(project, message, title, new String[]{okText, cancelText}, 0, icon, doNotAskOption) == 0 ? OK : CANCEL;
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(Project project, String message, @Nonnull String title, @Nonnull String okText, @Nonnull String cancelText, Image icon) {
    return showOkCancelDialog(project, message, title, okText, cancelText, icon, null);
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(Project project, String message, String title, Image icon) {
    return showOkCancelDialog(project, message, title, OK_BUTTON, CANCEL_BUTTON, icon);
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(@Nonnull Component parent, String message, @Nonnull String title, @Nonnull String okText, @Nonnull String cancelText, Image icon) {
    try {
      if (canShowMacSheetPanel()) {
        int result = MacMessages.getInstance().showYesNoDialog(title, message, okText, cancelText, TargetAWT.from(SwingUtilities.getWindowAncestor(parent)));
        return result == YES ? OK : CANCEL;
      }
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    return showDialog(parent, message, title, new String[]{okText, cancelText}, 0, icon) == 0 ? OK : CANCEL;
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(@Nonnull Component parent, String message, String title, Image icon) {
    return showOkCancelDialog(parent, message, title, OK_BUTTON, CANCEL_BUTTON, icon);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showOkCancelDialog(Project, String, String, Image)
   * @see #showOkCancelDialog(Component, String, String, Image)
   */
  @OkCancelResult
  public static int showOkCancelDialog(String message, String title, Image icon) {
    return showOkCancelDialog(message, title, OK_BUTTON, CANCEL_BUTTON, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showOkCancelDialog(Project, String, String, String, String, Image)
   * @see #showOkCancelDialog(Component, String, String, String, String, Image)
   */
  @OkCancelResult
  public static int showOkCancelDialog(String message, String title, String okText, String cancelText, Image icon) {
    return showOkCancelDialog(message, title, okText, cancelText, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showOkCancelDialog(Project, String, String, String, String, Image, DialogWrapper.DoNotAskOption)
   * @see #showOkCancelDialog(Component, String, String, String, String, Image)
   */
  @OkCancelResult
  public static int showOkCancelDialog(String message, @Nonnull String title, @Nonnull String okText, @Nonnull String cancelText, Image icon, @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        int result = MacMessages.getInstance().showYesNoDialog(title, message, okText, cancelText, null, doNotAskOption);
        return result == YES ? OK : CANCEL;
      }
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    return showDialog(message, title, new String[]{okText, cancelText}, 0, icon, doNotAskOption) == 0 ? OK : CANCEL;
  }

  public static int showCheckboxOkCancelDialog(String message, String title, String checkboxText, final boolean checked, final int defaultOptionIndex, final int focusedOptionIndex, Image icon) {
    return showCheckboxMessageDialog(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, checkboxText, checked, defaultOptionIndex, focusedOptionIndex, icon,
                                     new PairFunction<Integer, JCheckBox, Integer>() {
                                       @Override
                                       public Integer fun(final Integer exitCode, final JCheckBox cb) {
                                         return exitCode == -1 ? CANCEL : exitCode + (cb.isSelected() ? 1 : 0);
                                       }
                                     });
  }

  public static int showCheckboxMessageDialog(String message,
                                              String title,
                                              @Nonnull String[] options,
                                              String checkboxText,
                                              final boolean checked,
                                              final int defaultOptionIndex,
                                              final int focusedOptionIndex,
                                              Image icon,
                                              @Nullable final PairFunction<Integer, JCheckBox, Integer> exitFunc) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      TwoStepConfirmationDialog dialog = new TwoStepConfirmationDialog(message, title, options, checkboxText, checked, defaultOptionIndex, focusedOptionIndex, icon, exitFunc);
      dialog.show();
      return dialog.getExitCode();
    }
  }


  public static int showTwoStepConfirmationDialog(String message, String title, String checkboxText, Image icon) {
    return showCheckboxMessageDialog(message, title, new String[]{OK_BUTTON}, checkboxText, true, -1, -1, icon, null);
  }

  public static void showErrorDialog(@Nullable Project project, @Nls String message, @Nls @Nonnull String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (Exception exception) {
    }

    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showErrorDialog(@Nonnull Component component, String message, @Nls @Nonnull String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, TargetAWT.from(SwingUtilities.getWindowAncestor(component)));
        return;
      }
    }
    catch (Exception exception) {
    }

    showDialog(component, message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showErrorDialog(@Nonnull Component component, String message) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(CommonBundle.getErrorTitle(), message, OK_BUTTON, TargetAWT.from(SwingUtilities.getWindowAncestor(component)));
        return;
      }
    }
    catch (Exception exception) {
    }

    showDialog(component, message, CommonBundle.getErrorTitle(), new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showErrorDialog(Project, String, String)
   * @see #showErrorDialog(Component, String, String)
   */
  public static void showErrorDialog(String message, @Nonnull String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, null);
        return;
      }
    }
    catch (Exception exception) {
    }

    showDialog(message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showWarningDialog(@Nullable Project project, String message, @Nonnull String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (Exception exception) {
    }

    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  public static void showWarningDialog(@Nonnull Component component, String message, @Nonnull String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, TargetAWT.from(SwingUtilities.getWindowAncestor(component)));
        return;
      }
    }
    catch (Exception exception) {
    }

    showDialog(component, message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showWarningDialog(Project, String, String)
   * @see #showWarningDialog(Component, String, String)
   */
  public static void showWarningDialog(String message, @Nonnull String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, null);
        return;
      }
    }
    catch (Exception exception) {
    }

    showDialog(message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  @MagicConstant(intValues = {YES, NO, CANCEL})
  public @interface YesNoCancelResult {
  }


  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(Project project, String message, @Nonnull String title, @Nonnull String yes, @Nonnull String no, @Nonnull String cancel, @Nullable Image icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoCancelDialog(title, message, yes, no, cancel, WindowManager.getInstance().suggestParentWindow(project), null);
      }
    }
    catch (Exception exception) {
    }

    int buttonNumber = showDialog(project, message, title, new String[]{yes, no, cancel}, 0, icon);
    return buttonNumber == 0 ? YES : buttonNumber == 1 ? NO : CANCEL;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(Project project, String message, String title, Image icon) {
    return showYesNoCancelDialog(project, message, title, YES_BUTTON, NO_BUTTON, CANCEL_BUTTON, icon);
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@Nonnull Component parent, String message, @Nonnull String title, @Nonnull String yes, @Nonnull String no, @Nonnull String cancel, Image icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoCancelDialog(title, message, yes, no, cancel, TargetAWT.from(SwingUtilities.getWindowAncestor(parent)), null);
      }
    }
    catch (Exception exception) {
    }

    int buttonNumber = showDialog(parent, message, title, new String[]{yes, no, cancel}, 0, icon);
    return buttonNumber == 0 ? YES : buttonNumber == 1 ? NO : CANCEL;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@Nonnull Component parent, String message, String title, Image icon) {
    return showYesNoCancelDialog(parent, message, title, YES_BUTTON, NO_BUTTON, CANCEL_BUTTON, icon);
  }


  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showYesNoCancelDialog(Project, String, String, String, String, String, Image)
   * @see #showYesNoCancelDialog(Component, String, String, String, String, String, Image)
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(String message,
                                          @Nonnull String title,
                                          @Nonnull String yes,
                                          @Nonnull String no,
                                          @Nonnull String cancel,
                                          Image icon,
                                          @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoCancelDialog(title, message, yes, no, cancel, null, doNotAskOption);
      }
    }
    catch (Exception exception) {
    }

    int buttonNumber = showDialog(message, title, new String[]{yes, no, cancel}, 0, icon, doNotAskOption);
    return buttonNumber == 0 ? YES : buttonNumber == 1 ? NO : CANCEL;
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showYesNoCancelDialog(Project, String, String, String, String, String, Image)
   * @see #showYesNoCancelDialog(Component, String, String, String, String, String, Image)
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(String message, String title, String yes, String no, String cancel, Image icon) {
    return showYesNoCancelDialog(message, title, yes, no, cancel, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showYesNoCancelDialog(Project, String, String, Image)
   * @see #showYesNoCancelDialog(Component, String, String, Image)
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(String message, String title, Image icon) {
    return showYesNoCancelDialog(message, title, YES_BUTTON, NO_BUTTON, CANCEL_BUTTON, icon);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(@Nls String message, @Nls String title) {
    return showPasswordDialog(null, message, title, null, null);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(Project project, @Nls String message, @Nls String title, @Nullable Image icon) {
    return showPasswordDialog(project, message, title, icon, null);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(@Nullable Project project, @Nls String message, @Nls String title, @Nullable Image icon, @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }

    final InputDialog dialog = project != null ? new PasswordInputDialog(project, message, title, icon, validator) : new PasswordInputDialog(message, title, icon, validator);
    dialog.show();
    return dialog.getInputString();
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showInputDialog(@Nullable Project project, String message, String title, @Nullable Image icon) {
    return showInputDialog(project, message, title, icon, null, null);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showInputDialog(@Nonnull Component parent, String message, String title, @Nullable Image icon) {
    return showInputDialog(parent, message, title, icon, null, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Image)
   * @see #showInputDialog(Component, String, String, Image)
   */
  @Nullable
  public static String showInputDialog(String message, String title, @Nullable Image icon) {
    return showInputDialog(message, title, icon, null, null);
  }

  @Nullable
  public static String showInputDialog(@Nullable Project project, @Nls String message, @Nls String title, @Nullable Image icon, @Nullable String initialValue, @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }
    else {
      InputDialog dialog = new InputDialog(project, message, title, icon, initialValue, validator);
      dialog.show();
      return dialog.getInputString();
    }
  }

  @Nullable
  public static String showInputDialog(Project project,
                                       @Nls String message,
                                       @Nls String title,
                                       @Nullable Image icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator,
                                       @Nullable TextRange selection) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }
    else {
      InputDialog dialog = new InputDialog(project, message, title, icon, initialValue, validator);

      final JTextComponent field = dialog.getTextField();
      if (selection != null) {
        // set custom selection
        field.select(selection.getStartOffset(), selection.getEndOffset());
      }
      else {
        // reset selection
        final int length = field.getDocument().getLength();
        field.select(length, length);
      }
      field.putClientProperty(DialogWrapperPeer.HAVE_INITIAL_SELECTION, true);

      dialog.show();
      return dialog.getInputString();
    }
  }

  @Nullable
  public static String showInputDialog(@Nonnull Component parent, String message, String title, @Nullable Image icon, @Nullable String initialValue, @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }
    else {

      InputDialog dialog = new InputDialog(parent, message, title, icon, initialValue, validator);
      dialog.show();
      return dialog.getInputString();
    }
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Image, String, InputValidator)
   * @see #showInputDialog(Component, String, String, Image, String, InputValidator)
   */
  @Nullable
  public static String showInputDialog(String message, String title, @Nullable Image icon, @Nullable String initialValue, @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }
    else {
      InputDialog dialog = new InputDialog(message, title, icon, initialValue, validator);
      dialog.show();
      return dialog.getInputString();
    }
  }

  @Nullable
  public static String showMultilineInputDialog(Project project, String message, String title, @Nullable String initialValue, @Nullable Image icon, @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }
    InputDialog dialog = new MultilineInputDialog(project, message, title, icon, initialValue, validator, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0);
    dialog.show();
    return dialog.getInputString();
  }

  @Nonnull
  public static Pair<String, Boolean> showInputDialogWithCheckBox(String message,
                                                                  String title,
                                                                  String checkboxText,
                                                                  boolean checked,
                                                                  boolean checkboxEnabled,
                                                                  @Nullable Image icon,
                                                                  @NonNls String initialValue,
                                                                  @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return new Pair<String, Boolean>(ourTestInputImplementation.show(message), checked);
    }
    else {
      InputDialogWithCheckbox dialog = new InputDialogWithCheckbox(message, title, checkboxText, checked, checkboxEnabled, icon, initialValue, validator);
      dialog.show();
      return new Pair<String, Boolean>(dialog.getInputString(), dialog.isChecked());
    }
  }

  @Nullable
  public static String showEditableChooseDialog(String message, String title, @Nullable Image icon, String[] values, String initialValue, @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }
    else {
      ChooseDialog dialog = new ChooseDialog(message, title, icon, values, initialValue);
      dialog.setValidator(validator);
      dialog.getComboBox().setEditable(true);
      dialog.getComboBox().getEditor().setItem(initialValue);
      dialog.getComboBox().setSelectedItem(initialValue);
      dialog.show();
      return dialog.getInputString();
    }
  }

  /**
   * @deprecated It looks awful!
   */
  @Deprecated
  public static int showChooseDialog(String message, String title, String[] values, String initialValue, @Nullable Image icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      ChooseDialog dialog = new ChooseDialog(message, title, icon, values, initialValue);
      dialog.show();
      return dialog.getSelectedIndex();
    }
  }

  /**
   * @deprecated It looks awful!
   */
  @Deprecated
  public static int showChooseDialog(@Nonnull Component parent, String message, String title, String[] values, String initialValue, Image icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      ChooseDialog dialog = new ChooseDialog(parent, message, title, icon, values, initialValue);
      dialog.show();
      return dialog.getSelectedIndex();
    }
  }

  /**
   * @see com.intellij.openapi.ui.DialogWrapper#DialogWrapper(Project, boolean)
   * @deprecated It looks awful!
   */
  @Deprecated
  public static int showChooseDialog(Project project, String message, String title, Image icon, String[] values, String initialValue) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      ChooseDialog dialog = new ChooseDialog(project, message, title, icon, values, initialValue);
      dialog.show();
      return dialog.getSelectedIndex();
    }
  }

  /**
   * Shows dialog with given message and title, information icon {@link #getInformationImage ()} and OK button
   */
  public static void showInfoMessage(Component component, String message, @Nonnull String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, TargetAWT.from(SwingUtilities.getWindowAncestor(component)));
        return;
      }
    }
    catch (Exception exception) {
    }

    showMessageDialog(component, message, title, getInformationIcon());
  }

  /**
   * Shows dialog with given message and title, information icon {@link #getInformationImage ()} and OK button
   */
  public static void showInfoMessage(@Nullable Project project, @Nls String message, @Nls @Nonnull String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (Exception exception) {
    }

    showMessageDialog(project, message, title, getInformationIcon());
  }

  /**
   * Shows dialog with given message and title, information icon {@link #getInformationImage ()} and OK button
   * <p>
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Image, String, InputValidator)
   * @see #showInputDialog(Component, String, String, Image, String, InputValidator)
   */
  public static void showInfoMessage(String message, @Nonnull String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, null);
        return;
      }
    }
    catch (Exception exception) {
    }

    showMessageDialog(message, title, getInformationIcon());
  }

  /**
   * Shows dialog with text area to edit long strings that don't fit in text field.
   */
  public static void showTextAreaDialog(final JTextField textField,
                                        final String title,
                                        @NonNls final String dimensionServiceKey,
                                        final Function<String, List<String>> parser,
                                        final Function<List<String>, String> lineJoiner) {
    if (isApplicationInUnitTestOrHeadless()) {
      ourTestImplementation.show(title);
    }
    else {
      final JTextArea textArea = new JTextArea(10, 50);
      textArea.setWrapStyleWord(true);
      textArea.setLineWrap(true);
      List<String> lines = parser.apply(textField.getText());
      textArea.setText(StringUtil.join(lines, "\n"));
      InsertPathAction.copyFromTo(textField, textArea);
      final DialogBuilder builder = new DialogBuilder(textField);
      builder.setDimensionServiceKey(dimensionServiceKey);
      builder.setCenterPanel(ScrollPaneFactory.createScrollPane(textArea));
      builder.setPreferredFocusComponent(textArea);
      String rawText = title;
      if (StringUtil.endsWithChar(rawText, ':')) {
        rawText = rawText.substring(0, rawText.length() - 1);
      }
      builder.setTitle(rawText);
      builder.addOkAction();
      builder.addCancelAction();
      builder.setOkOperation(new Runnable() {
        @Override
        public void run() {
          textField.setText(lineJoiner.apply(Arrays.asList(StringUtil.splitByLines(textArea.getText()))));
          builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
        }
      });
      builder.addDisposable(new TextComponentUndoProvider(textArea));
      builder.show();
    }
  }

  public static void showTextAreaDialog(final JTextField textField, final String title, @NonNls final String dimensionServiceKey) {
    showTextAreaDialog(textField, title, dimensionServiceKey, ParametersListUtil.DEFAULT_LINE_PARSER, ParametersListUtil.DEFAULT_LINE_JOINER);
  }

  private static class MoreInfoMessageDialog extends MessageDialog {
    @Nullable
    private final String myInfoText;

    public MoreInfoMessageDialog(Project project, String message, String title, @Nullable String moreInfo, @Nonnull String[] options, int defaultOptionIndex, int focusedOptionIndex, Image icon) {
      super(project);
      myInfoText = moreInfo;
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, null);
    }

    @Override
    protected JComponent createNorthPanel() {
      return doCreateCenterPanel();
    }

    @Override
    protected JComponent createCenterPanel() {
      if (myInfoText == null) {
        return null;
      }
      final JPanel panel = new JPanel(new BorderLayout());
      final JTextArea area = new JTextArea(myInfoText);
      area.setEditable(false);
      final JBScrollPane scrollPane = new JBScrollPane(area) {
        @Override
        public Dimension getPreferredSize() {
          final Dimension preferredSize = super.getPreferredSize();
          final Container parent = getParent();
          if (parent != null) {
            return new Dimension(preferredSize.width, Math.min(150, preferredSize.height));
          }
          return preferredSize;
        }
      };
      panel.add(scrollPane);
      return panel;
    }
  }

  private static class MessageDialog extends DialogWrapper {
    protected String myMessage;
    protected String[] myOptions;
    protected int myDefaultOptionIndex;
    protected int myFocusedOptionIndex;
    protected Image myImage;
    private MyBorderLayout myLayout;

    public MessageDialog(@Nullable Project project, String message, String title, @Nonnull String[] options, int defaultOptionIndex, @Nullable Image icon, boolean canBeParent) {
      this(project, message, title, options, defaultOptionIndex, -1, icon, canBeParent);
    }

    public MessageDialog(@Nullable Project project,
                         String message,
                         String title,
                         @Nonnull String[] options,
                         int defaultOptionIndex,
                         int focusedOptionIndex,
                         @Nullable Image icon,
                         @Nullable DoNotAskOption doNotAskOption,
                         boolean canBeParent) {
      super(project, canBeParent);
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption);
    }

    public MessageDialog(@Nullable Project project,
                         String message,
                         String title,
                         @Nonnull String[] options,
                         int defaultOptionIndex,
                         int focusedOptionIndex,
                         @Nullable Image icon,
                         boolean canBeParent) {
      super(project, canBeParent);
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, null);
    }

    public MessageDialog(@Nonnull Component parent, String message, String title, @Nonnull String[] options, int defaultOptionIndex, @Nullable Image icon) {
      this(parent, message, title, options, defaultOptionIndex, icon, false);
    }

    public MessageDialog(@Nonnull Component parent, String message, String title, @Nonnull String[] options, int defaultOptionIndex, @Nullable Image icon, boolean canBeParent) {
      this(parent, message, title, options, defaultOptionIndex, -1, icon, canBeParent);
    }

    public MessageDialog(@Nonnull Component parent,
                         String message,
                         String title,
                         @Nonnull String[] options,
                         int defaultOptionIndex,
                         int focusedOptionIndex,
                         @Nullable Image icon,
                         boolean canBeParent) {
      super(parent, canBeParent);
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, null);
    }

    public MessageDialog(String message, String title, @Nonnull String[] options, int defaultOptionIndex, @Nullable Image icon) {
      this(message, title, options, defaultOptionIndex, icon, false);
    }

    public MessageDialog(String message, String title, @Nonnull String[] options, int defaultOptionIndex, @Nullable Image icon, boolean canBeParent) {
      super(canBeParent);
      _init(title, message, options, defaultOptionIndex, -1, icon, null);
    }

    public MessageDialog(String message, String title, @Nonnull String[] options, int defaultOptionIndex, int focusedOptionIndex, @Nullable Image icon, @Nullable DoNotAskOption doNotAskOption) {
      super(false);
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption);
    }

    public MessageDialog(String message, String title, @Nonnull String[] options, int defaultOptionIndex, Image icon, DoNotAskOption doNotAskOption) {
      this(message, title, options, defaultOptionIndex, -1, icon, doNotAskOption);
    }

    protected MessageDialog() {
      super(false);
    }

    protected MessageDialog(Project project) {
      super(project, false);
    }

    protected void _init(String title, String message, @Nonnull String[] options, int defaultOptionIndex, int focusedOptionIndex, @Nullable Image icon, @Nullable DoNotAskOption doNotAskOption) {
      setTitle(title);
      if (isMacSheetEmulation()) {
        setUndecorated(true);
      }
      myMessage = message;
      myOptions = options;
      myDefaultOptionIndex = defaultOptionIndex;
      myFocusedOptionIndex = focusedOptionIndex;
      myImage = icon;
      setButtonsAlignment(SwingConstants.CENTER);
      setDoNotAskOption(doNotAskOption);
      init();
      if (isMacSheetEmulation()) {
        MacUtil.adjustFocusTraversal(myDisposable);
      }
    }

    @Nonnull
    @Override
    protected Action[] createActions() {
      Action[] actions = new Action[myOptions.length];
      for (int i = 0; i < myOptions.length; i++) {
        String option = myOptions[i];
        final int exitCode = i;
        actions[i] = new AbstractAction(UIUtil.replaceMnemonicAmpersand(option)) {
          @Override
          public void actionPerformed(ActionEvent e) {
            close(exitCode, true);
          }
        };

        if (i == myDefaultOptionIndex) {
          actions[i].putValue(DEFAULT_ACTION, Boolean.TRUE);
        }

        if (i == myFocusedOptionIndex) {
          actions[i].putValue(FOCUSED_ACTION, Boolean.TRUE);
        }

        UIUtil.assignMnemonic(option, actions[i]);

      }
      return actions;
    }

    @Override
    public void doCancelAction() {
      close(-1);
    }

    @Override
    protected JComponent createCenterPanel() {
      return doCreateCenterPanel();
    }

    @Override
    protected LayoutManager createRootLayout() {
      return isMacSheetEmulation() ? myLayout = new MyBorderLayout() : super.createRootLayout();
    }

    @Override
    protected void dispose() {
      if (isMacSheetEmulation()) {
        animate();
      }
      else {
        super.dispose();
      }
    }

    @Override
    public void show() {
      if (isMacSheetEmulation()) {
        setInitialLocationCallback(new Computable<Point>() {
          @Override
          public Point compute() {
            JRootPane rootPane = SwingUtilities.getRootPane(getWindow().getParent());
            if (rootPane == null) {
              rootPane = SwingUtilities.getRootPane(getWindow().getOwner());
            }

            Point p = rootPane.getLocationOnScreen();
            p.x += (rootPane.getWidth() - getWindow().getWidth()) / 2;
            return p;
          }
        });
        animate();
        if (SystemInfo.isJavaVersionAtLeast(7, 0, 0)) {
          try {
            Method method = Class.forName("java.awt.Window").getDeclaredMethod("setOpacity", float.class);
            if (method != null) method.invoke(getPeer().getWindow(), .8f);
          }
          catch (Exception exception) {
          }
        }
        setAutoAdjustable(false);
        setSize(getPreferredSize().width, 0);//initial state before animation, zero height
      }
      super.show();
    }

    private void animate() {
      final int height = getPreferredSize().height;
      final int frameCount = 10;
      final boolean toClose = isShowing();


      final AtomicInteger i = new AtomicInteger(-1);
      final Alarm animator = new Alarm(myDisposable);
      final Runnable runnable = new Runnable() {
        @Override
        public void run() {
          int state = i.addAndGet(1);

          double linearProgress = (double)state / frameCount;
          if (toClose) {
            linearProgress = 1 - linearProgress;
          }
          myLayout.myPhase = (1 - Math.cos(Math.PI * linearProgress)) / 2;
          Window window = getPeer().getWindow();
          Rectangle bounds = window.getBounds();
          bounds.height = (int)(height * myLayout.myPhase);

          window.setBounds(bounds);

          if (state == 0 && !toClose && window.getOwner() != null) {
            consulo.ui.Window uiWindow = TargetAWT.from(window.getOwner());
            IdeFrame ideFrame = uiWindow.getUserData(IdeFrame.KEY);
            if (ideFrame != null) {
              WindowManager.getInstance().requestUserAttention(ideFrame, true);
            }
          }

          if (state < frameCount) {
            animator.addRequest(this, 10);
          }
          else if (toClose) {
            MessageDialog.super.dispose();
          }
        }
      };
      animator.addRequest(runnable, 10, ModalityState.stateForComponent(getRootPane()));
    }

    protected JComponent doCreateCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout(15, 0));
      if (myImage != null) {
        JLabel iconLabel = new JBLabel(myImage);
        Container container = new Container();
        container.setLayout(new BorderLayout());
        container.add(iconLabel, BorderLayout.NORTH);
        panel.add(container, BorderLayout.WEST);
      }
      if (myMessage != null) {
        final JTextPane messageComponent = createMessageComponent(myMessage);

        final Dimension screenSize = messageComponent.getToolkit().getScreenSize();
        final Dimension textSize = messageComponent.getPreferredSize();
        if (myMessage.length() > 100) {
          final JScrollPane pane = ScrollPaneFactory.createScrollPane(messageComponent);
          pane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
          pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
          pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
          final int scrollSize = (int)new JScrollBar(Adjustable.VERTICAL).getPreferredSize().getWidth();
          final Dimension preferredSize = new Dimension(Math.min(textSize.width, screenSize.width / 2) + scrollSize, Math.min(textSize.height, screenSize.height / 3) + scrollSize);
          pane.setPreferredSize(preferredSize);
          panel.add(pane, BorderLayout.CENTER);
        }
        else {
          panel.add(messageComponent, BorderLayout.CENTER);
        }
      }
      return panel;
    }

    protected static JTextPane createMessageComponent(final String message) {
      final JTextPane messageComponent = new JTextPane();
      return configureMessagePaneUi(messageComponent, message);
    }

    @Override
    protected void doHelpAction() {
      // do nothing
    }
  }

  private static class MyBorderLayout extends BorderLayout {
    private double myPhase = 0;//it varies from 0 (hidden state) to 1 (fully visible)

    private MyBorderLayout() {
    }

    @Override
    public void layoutContainer(Container target) {
      final Dimension realSize = target.getSize();
      target.setSize(target.getPreferredSize());

      super.layoutContainer(target);

      target.setSize(realSize);

      synchronized (target.getTreeLock()) {
        int yShift = (int)((1 - myPhase) * target.getPreferredSize().height);
        Component[] components = target.getComponents();
        for (Component component : components) {
          Point point = component.getLocation();
          point.y -= yShift;
          component.setLocation(point);
        }
      }
    }
  }

  public static void installHyperlinkSupport(JTextPane messageComponent) {
    configureMessagePaneUi(messageComponent, "<html></html>");
  }

  @Nonnull
  public static JTextPane configureMessagePaneUi(JTextPane messageComponent, String message) {
    JTextPane pane = configureMessagePaneUi(messageComponent, message, null);
    if (UIUtil.HTML_MIME.equals(pane.getContentType())) {
      pane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    }
    return pane;
  }

  @Nonnull
  public static JTextPane configureMessagePaneUi(@Nonnull JTextPane messageComponent, @Nullable String message, @Nullable UIUtil.FontSize fontSize) {
    UIUtil.FontSize fixedFontSize = fontSize == null ? UIUtil.FontSize.NORMAL : fontSize;
    messageComponent.setFont(UIUtil.getLabelFont(fixedFontSize));
    if (BasicHTML.isHTMLString(message)) {
      HTMLEditorKit editorKit = new HTMLEditorKit();
      Font font = UIUtil.getLabelFont(fixedFontSize);
      editorKit.getStyleSheet().addRule(UIUtil.displayPropertiesToCSS(font, UIUtil.getLabelForeground()));
      messageComponent.setEditorKit(editorKit);
      messageComponent.setContentType(UIUtil.HTML_MIME);
    }
    messageComponent.setText(message);
    messageComponent.setEditable(false);
    if (messageComponent.getCaret() != null) {
      messageComponent.setCaretPosition(0);
    }

    if (UIUtil.isUnderNimbusLookAndFeel()) {
      messageComponent.setOpaque(false);
      messageComponent.setBackground(UIUtil.TRANSPARENT_COLOR);
    }
    else {
      messageComponent.setBackground(UIUtil.getOptionPaneBackground());
    }

    messageComponent.setForeground(UIUtil.getLabelForeground());
    return messageComponent;
  }

  protected static class TwoStepConfirmationDialog extends MessageDialog {
    private JCheckBox myCheckBox;
    private final String myCheckboxText;
    private final boolean myChecked;
    private final PairFunction<Integer, JCheckBox, Integer> myExitFunc;

    public TwoStepConfirmationDialog(String message,
                                     String title,
                                     @Nonnull String[] options,
                                     String checkboxText,
                                     boolean checked,
                                     final int defaultOptionIndexed,
                                     final int focusedOptionIndex,
                                     Image icon,
                                     @Nullable final PairFunction<Integer, JCheckBox, Integer> exitFunc) {
      myCheckboxText = checkboxText;
      myChecked = checked;
      myExitFunc = exitFunc;

      _init(title, message, options, defaultOptionIndexed, focusedOptionIndex, icon, null);
    }

    @Override
    protected JComponent createNorthPanel() {
      JPanel panel = new JPanel(new BorderLayout(15, 0));
      if (myImage != null) {
        JLabel iconLabel = new JBLabel(myImage);
        Container container = new Container();
        container.setLayout(new BorderLayout());
        container.add(iconLabel, BorderLayout.NORTH);
        panel.add(container, BorderLayout.WEST);
      }

      JPanel messagePanel = new JPanel(new BorderLayout());
      if (myMessage != null) {
        JLabel textLabel = new JLabel(myMessage);
        textLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        textLabel.setUI(new MultiLineLabelUI());
        messagePanel.add(textLabel, BorderLayout.NORTH);
      }

      final JPanel checkboxPanel = new JPanel();
      checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.X_AXIS));

      myCheckBox = new JCheckBox(myCheckboxText);
      myCheckBox.setSelected(myChecked);
      messagePanel.add(myCheckBox, BorderLayout.SOUTH);
      panel.add(messagePanel, BorderLayout.CENTER);

      return panel;
    }

    @Override
    public int getExitCode() {
      final int exitCode = super.getExitCode();
      if (myExitFunc != null) {
        return myExitFunc.fun(exitCode, myCheckBox);
      }

      return exitCode == OK_EXIT_CODE ? myCheckBox.isSelected() ? OK_EXIT_CODE : CANCEL_EXIT_CODE : CANCEL_EXIT_CODE;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myDefaultOptionIndex == -1 ? myCheckBox : super.getPreferredFocusedComponent();
    }

    @Override
    protected JComponent createCenterPanel() {
      return null;
    }
  }

  protected static class InputDialog extends MessageDialog {
    protected JTextComponent myField;
    private final InputValidator myValidator;

    public InputDialog(@Nullable Project project,
                       String message,
                       String title,
                       @Nullable Image icon,
                       @Nullable String initialValue,
                       @Nullable InputValidator validator,
                       @Nonnull String[] options,
                       int defaultOption) {
      super(project, message, title, options, defaultOption, icon, true);
      myValidator = validator;
      myField.setText(initialValue);
    }

    public InputDialog(@Nullable Project project, String message, String title, @Nullable Image icon, @Nullable String initialValue, @Nullable InputValidator validator) {
      this(project, message, title, icon, initialValue, validator, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0);
    }

    public InputDialog(@Nonnull Component parent, String message, String title, @Nullable Image icon, @Nullable String initialValue, @Nullable InputValidator validator) {
      super(parent, message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon, true);
      myValidator = validator;
      myField.setText(initialValue);
    }

    public InputDialog(String message, String title, @Nullable Image icon, @Nullable String initialValue, @Nullable InputValidator validator) {
      super(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon, true);
      myValidator = validator;
      myField.setText(initialValue);
    }

    @Nonnull
    @Override
    protected Action[] createActions() {
      final Action[] actions = new Action[myOptions.length];
      for (int i = 0; i < myOptions.length; i++) {
        String option = myOptions[i];
        final int exitCode = i;
        if (i == 0) { // "OK" is default button. It has index 0.
          actions[i] = getOKAction();
          actions[i].putValue(DEFAULT_ACTION, Boolean.TRUE);
          myField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            public void textChanged(DocumentEvent event) {
              final String text = myField.getText().trim();
              actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(text));
              if (myValidator instanceof InputValidatorEx) {
                setErrorText(((InputValidatorEx)myValidator).getErrorText(text));
              }
            }
          });
        }
        else {
          actions[i] = new AbstractAction(option) {
            @Override
            public void actionPerformed(ActionEvent e) {
              close(exitCode);
            }
          };
        }
      }
      return actions;
    }

    @Override
    protected void doOKAction() {
      String inputString = myField.getText().trim();
      if (myValidator == null || myValidator.checkInput(inputString) && myValidator.canClose(inputString)) {
        close(0);
      }
    }

    @Override
    protected JComponent createCenterPanel() {
      return null;
    }

    @Override
    protected JComponent createNorthPanel() {
      JPanel panel = new JPanel(new BorderLayout(15, 0));
      if (myImage != null) {
        JLabel iconLabel = new JBLabel(myImage);
        Container container = new Container();
        container.setLayout(new BorderLayout());
        container.add(iconLabel, BorderLayout.NORTH);
        panel.add(container, BorderLayout.WEST);
      }

      JPanel messagePanel = createMessagePanel();
      panel.add(messagePanel, BorderLayout.CENTER);

      return panel;
    }

    protected JPanel createMessagePanel() {
      JPanel messagePanel = new JPanel(new BorderLayout());
      if (myMessage != null) {
        JComponent textComponent = createTextComponent();
        messagePanel.add(textComponent, BorderLayout.NORTH);
      }

      myField = createTextFieldComponent();
      messagePanel.add(myField, BorderLayout.SOUTH);

      return messagePanel;
    }

    protected JComponent createTextComponent() {
      JComponent textComponent;
      if (BasicHTML.isHTMLString(myMessage)) {
        textComponent = createMessageComponent(myMessage);
      }
      else {
        JLabel textLabel = new JLabel(myMessage);
        textLabel.setUI(new MultiLineLabelUI());
        textComponent = textLabel;
      }
      textComponent.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
      return textComponent;
    }

    public JTextComponent getTextField() {
      return myField;
    }

    protected JTextComponent createTextFieldComponent() {
      return new JTextField(30);
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myField;
    }

    @Nullable
    public String getInputString() {
      if (getExitCode() == 0) {
        return myField.getText().trim();
      }
      return null;
    }
  }

  protected static class MultilineInputDialog extends InputDialog {
    public MultilineInputDialog(Project project,
                                String message,
                                String title,
                                @Nullable Image icon,
                                @Nullable String initialValue,
                                @Nullable InputValidator validator,
                                @Nonnull String[] options,
                                int defaultOption) {
      super(project, message, title, icon, initialValue, validator, options, defaultOption);
    }

    @Override
    protected JTextComponent createTextFieldComponent() {
      return new JTextArea(7, 50);
    }
  }

  protected static class PasswordInputDialog extends InputDialog {
    public PasswordInputDialog(String message, String title, @Nullable Image icon, @Nullable InputValidator validator) {
      super(message, title, icon, null, validator);
    }

    public PasswordInputDialog(Project project, String message, String title, @Nullable Image icon, @Nullable InputValidator validator) {
      super(project, message, title, icon, null, validator);
    }

    @Override
    protected JTextComponent createTextFieldComponent() {
      return new JPasswordField(30);
    }
  }

  protected static class InputDialogWithCheckbox extends InputDialog {
    private JCheckBox myCheckBox;

    public InputDialogWithCheckbox(String message,
                                   String title,
                                   String checkboxText,
                                   boolean checked,
                                   boolean checkboxEnabled,
                                   @Nullable Image icon,
                                   @Nullable String initialValue,
                                   @Nullable InputValidator validator) {
      super(message, title, icon, initialValue, validator);
      myCheckBox.setText(checkboxText);
      myCheckBox.setSelected(checked);
      myCheckBox.setEnabled(checkboxEnabled);
    }

    @Override
    protected JPanel createMessagePanel() {
      JPanel messagePanel = new JPanel(new BorderLayout());
      if (myMessage != null) {
        JComponent textComponent = createTextComponent();
        messagePanel.add(textComponent, BorderLayout.NORTH);
      }

      myField = createTextFieldComponent();
      messagePanel.add(myField, BorderLayout.CENTER);

      myCheckBox = new JCheckBox();
      messagePanel.add(myCheckBox, BorderLayout.SOUTH);

      return messagePanel;
    }

    public Boolean isChecked() {
      return myCheckBox.isSelected();
    }
  }

  /**
   * It looks awful!
   */
  @Deprecated
  protected static class ChooseDialog extends MessageDialog {
    private ComboBox myComboBox;
    private InputValidator myValidator;

    public ChooseDialog(Project project, String message, String title, @Nullable Image icon, String[] values, String initialValue, @Nonnull String[] options, int defaultOption) {
      super(project, message, title, options, defaultOption, icon, true);
      myComboBox.setModel(new DefaultComboBoxModel(values));
      myComboBox.setSelectedItem(initialValue);
    }

    public ChooseDialog(Project project, String message, String title, @Nullable Image icon, String[] values, String initialValue) {
      this(project, message, title, icon, values, initialValue, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0);
    }

    public ChooseDialog(@Nonnull Component parent, String message, String title, @Nullable Image icon, String[] values, String initialValue) {
      super(parent, message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
      myComboBox.setModel(new DefaultComboBoxModel(values));
      myComboBox.setSelectedItem(initialValue);
    }

    public ChooseDialog(String message, String title, @Nullable Image icon, String[] values, String initialValue) {
      super(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
      myComboBox.setModel(new DefaultComboBoxModel(values));
      myComboBox.setSelectedItem(initialValue);
    }

    @Nonnull
    @Override
    protected Action[] createActions() {
      final Action[] actions = new Action[myOptions.length];
      for (int i = 0; i < myOptions.length; i++) {
        String option = myOptions[i];
        final int exitCode = i;
        if (i == myDefaultOptionIndex) {
          actions[i] = new AbstractAction(option) {
            @Override
            public void actionPerformed(ActionEvent e) {
              if (myValidator == null || myValidator.checkInput(myComboBox.getSelectedItem().toString().trim())) {
                close(exitCode);
              }
            }
          };
          actions[i].putValue(DEFAULT_ACTION, Boolean.TRUE);
          myComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
              actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(myComboBox.getSelectedItem().toString().trim()));
            }
          });
          final JTextField textField = (JTextField)myComboBox.getEditor().getEditorComponent();
          textField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            public void textChanged(DocumentEvent event) {
              actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(textField.getText().trim()));
            }
          });
        }
        else { // "Cancel" action
          actions[i] = new AbstractAction(option) {
            @Override
            public void actionPerformed(ActionEvent e) {
              close(exitCode);
            }
          };
        }
      }
      return actions;
    }

    @Override
    protected JComponent createCenterPanel() {
      return null;
    }

    @Override
    protected JComponent createNorthPanel() {
      JPanel panel = new JPanel(new BorderLayout(15, 0));
      if (myImage != null) {
        JLabel iconLabel = new JBLabel(myImage);
        Container container = new Container();
        container.setLayout(new BorderLayout());
        container.add(iconLabel, BorderLayout.NORTH);
        panel.add(container, BorderLayout.WEST);
      }

      JPanel messagePanel = new JPanel(new BorderLayout());
      if (myMessage != null) {
        JLabel textLabel = new JLabel(myMessage);
        textLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        textLabel.setUI(new MultiLineLabelUI());
        messagePanel.add(textLabel, BorderLayout.NORTH);
      }

      myComboBox = new ComboBox(220);
      messagePanel.add(myComboBox, BorderLayout.SOUTH);
      panel.add(messagePanel, BorderLayout.CENTER);
      return panel;
    }

    @Override
    protected void doOKAction() {
      String inputString = myComboBox.getSelectedItem().toString().trim();
      if (myValidator == null || myValidator.checkInput(inputString) && myValidator.canClose(inputString)) {
        super.doOKAction();
      }
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myComboBox;
    }

    @Nullable
    public String getInputString() {
      if (getExitCode() == 0) {
        return myComboBox.getSelectedItem().toString();
      }
      return null;
    }

    public int getSelectedIndex() {
      if (getExitCode() == 0) {
        return myComboBox.getSelectedIndex();
      }
      return -1;
    }

    public JComboBox getComboBox() {
      return myComboBox;
    }

    public void setValidator(@Nullable InputValidator validator) {
      myValidator = validator;
    }
  }
}
