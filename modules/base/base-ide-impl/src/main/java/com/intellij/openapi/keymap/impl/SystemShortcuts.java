// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.openapi.actionSystem.*;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.KeymapPanel;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import consulo.application.util.registry.Registry;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ReflectionUtil;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

public final class SystemShortcuts {
  private static final Logger LOG = Logger.getInstance(SystemShortcuts.class);
  private static final
  @Nonnull
  String ourNotificationGroupId = "System shortcuts conflicts";
  private static final
  @Nonnull
  String ourUnknownSysAction = "Unknown action";

  private static boolean ourIsNotificationRegistered = false;

  private
  @Nonnull
  final Map<KeyStroke, AWTKeyStroke> myKeyStroke2SysShortcut = new HashMap<>();
  private
  @Nonnull
  final MuteConflictsSettings myMutedConflicts = new MuteConflictsSettings();
  private
  @Nonnull
  final Set<String> myNotifiedActions = new HashSet<>();

  private
  @Nullable
  Keymap myKeymap;

  @Nonnull
  private final Map<AWTKeyStroke, ConflictItem> myKeymapConflicts = new HashMap<>();

  public SystemShortcuts() {
    readSystem();
  }

  public static final class ConflictItem {
    final
    @Nonnull
    String mySysActionDesc;
    final
    @Nonnull
    KeyStroke mySysKeyStroke;
    final
    @Nonnull
    String[] myActionIds;

    public ConflictItem(@Nonnull KeyStroke sysKeyStroke, @Nonnull String sysActionDesc, @Nonnull String[] actionIds) {
      mySysKeyStroke = sysKeyStroke;
      mySysActionDesc = sysActionDesc;
      myActionIds = actionIds;
    }

    @Nonnull
    public String getSysActionDesc() {
      return mySysActionDesc;
    }

    @Nonnull
    public KeyStroke getSysKeyStroke() {
      return mySysKeyStroke;
    }

    @Nonnull
    public String[] getActionIds() {
      return myActionIds;
    }

    @Nullable
    String getUnmutedActionId(@Nonnull MuteConflictsSettings settings) {
      for (String actId : myActionIds)
        if (!settings.isMutedAction(actId)) return actId;
      return null;
    }
  }

  public void updateKeymapConflicts(@Nullable Keymap keymap) {
    myKeymap = keymap;
    myKeymapConflicts.clear();

    if (myKeyStroke2SysShortcut.isEmpty()) return;

    for (@Nonnull KeyStroke sysKS : myKeyStroke2SysShortcut.keySet()) {
      final String[] actIds = computeOnEdt(() -> keymap.getActionIds(sysKS));
      if (actIds == null || actIds.length == 0) continue;

      @Nonnull AWTKeyStroke shk = myKeyStroke2SysShortcut.get(sysKS);
      myKeymapConflicts.put(shk, new ConflictItem(sysKS, getDescription(shk), actIds));
    }
  }

  @Nonnull
  public Collection<ConflictItem> getUnmutedKeymapConflicts() {
    List<ConflictItem> result = new ArrayList<>();
    myKeymapConflicts.forEach((ks, ci) -> {
      if (ci.getUnmutedActionId(myMutedConflicts) != null) result.add(ci);
    });
    return result;
  }

  @Nullable
  public Condition<AnAction> createKeymapConflictsActionFilter() {
    if (myKeyStroke2SysShortcut.isEmpty() || myKeymap == null) return null;

    final Condition<Shortcut> predicat = sc -> {
      if (sc == null) return false;
      for (KeyStroke ks : myKeyStroke2SysShortcut.keySet()) {
        if (sc.startsWith(new KeyboardShortcut(ks, null))) {
          final ConflictItem ci = myKeymapConflicts.get(myKeyStroke2SysShortcut.get(ks));
          if (ci != null && ci.getUnmutedActionId(myMutedConflicts) != null) return true;
        }
      }
      return false;
    };
    return ActionsTreeUtil.isActionFiltered(ActionManager.getInstance(), myKeymap, predicat);
  }

  public
  @Nullable
  Map<KeyboardShortcut, String> calculateConflicts(@Nonnull Keymap keymap, @Nonnull String actionId) {
    if (myKeyStroke2SysShortcut.isEmpty()) return null;

    Map<KeyboardShortcut, String> result = null;
    final Shortcut[] actionShortcuts = computeOnEdt(() -> keymap.getShortcuts(actionId));
    for (Shortcut sc : actionShortcuts) {
      if (!(sc instanceof KeyboardShortcut)) {
        continue;
      }
      final KeyboardShortcut ksc = (KeyboardShortcut)sc;
      for (@Nonnull KeyStroke sks : myKeyStroke2SysShortcut.keySet()) {
        if (ksc.getFirstKeyStroke().equals(sks) || sks.equals(ksc.getSecondKeyStroke())) {
          if (result == null) result = new HashMap<>();
          result.put(ksc, getDescription(myKeyStroke2SysShortcut.get(sks)));
        }
      }
    }
    return result;
  }

  private static <T> T computeOnEdt(Supplier<T> supplier) {
    final Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      return supplier.get();
    }

    final Ref<T> result = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      result.set(supplier.get());
    });
    return result.get();
  }

  public
  @Nullable
  Map<KeyStroke, String> createKeystroke2SysShortcutMap() {
    if (myKeyStroke2SysShortcut.isEmpty()) return null;

    final Map<KeyStroke, String> result = new HashMap<>();
    myKeyStroke2SysShortcut.forEach((ks, sysks) -> result.put(ks, getDescription(sysks)));
    return result;
  }

  private int getUnmutedConflictsCount() {
    if (myKeymapConflicts.isEmpty()) return 0;
    int result = 0;
    for (ConflictItem ci : myKeymapConflicts.values())
      if (ci.getUnmutedActionId(myMutedConflicts) != null) result++;
    return result;
  }

  public void onUserPressedShortcut(@Nonnull Keymap keymap, @Nonnull String[] actionIds, @Nonnull KeyboardShortcut ksc) {
    if (actionIds.length == 0) return;

    KeyStroke ks = ksc.getFirstKeyStroke();
    AWTKeyStroke sysKs = myKeyStroke2SysShortcut.get(ks);
    if (sysKs == null && ksc.getSecondKeyStroke() != null) sysKs = myKeyStroke2SysShortcut.get(ks = ksc.getSecondKeyStroke());
    if (sysKs == null) return;

    String unmutedActId = null;
    for (String actId : actionIds) {
      if (myNotifiedActions.contains(actId)) {
        continue;
      }
      if (!myMutedConflicts.isMutedAction(actId)) {
        unmutedActId = actId;
        break;
      }
    }
    if (unmutedActId == null) return;

    final @Nullable String macOsShortcutAction = getDescription(sysKs);
    //System.out.println(actionId + " shortcut '" + sysKS + "' "
    //                   + Arrays.toString(actionShortcuts) + " conflicts with macOS shortcut"
    //                   + (macOsShortcutAction == null ? "." : " '" + macOsShortcutAction + "'."));
    doNotify(keymap, unmutedActId, ks, macOsShortcutAction, ksc);
  }

  private void doNotify(@Nonnull Keymap keymap, @Nonnull String actionId, @Nonnull KeyStroke sysKS, @Nullable String macOsShortcutAction, @Nonnull KeyboardShortcut conflicted) {
    if (!ourIsNotificationRegistered) {
      ourIsNotificationRegistered = true;
      NotificationsConfiguration.getNotificationsConfiguration().register(ourNotificationGroupId, NotificationDisplayType.STICKY_BALLOON, true);
    }

    final AnAction act = ActionManager.getInstance().getAction(actionId);
    final String actText = act == null ? actionId : act.getTemplateText();
    final String message = "The " +
                           actText +
                           " shortcut conflicts with macOS shortcut" +
                           (macOsShortcutAction == null ? "" : " '" + macOsShortcutAction + "'") +
                           ". Modify this shortcut or change macOS system settings.";
    final Notification notification = new Notification(ourNotificationGroupId, "Shortcuts conflicts", message, NotificationType.WARNING, null);

    final AnAction configureShortcut = DumbAwareAction.create("Modify shortcut", e -> {
      Component component = e.getDataContext().getData(PlatformDataKeys.CONTEXT_COMPONENT);
      if (component == null) {
        Window[] frames = Window.getWindows();
        component = frames == null || frames.length == 0 ? null : frames[0];
        if (component == null) {
          LOG.error("can't show KeyboardShortcutDialog (parent component wasn't found)");
          return;
        }
      }

      KeymapPanel.addKeyboardShortcut(actionId, ActionShortcutRestrictions.getInstance().getForActionId(actionId), keymap, component, conflicted, SystemShortcuts.this);
      notification.expire();
    });
    notification.addAction(configureShortcut);

    final AnAction muteAction = DumbAwareAction.create("Don't show again", e -> {
      myMutedConflicts.addMutedAction(actionId);
      notification.expire();
    });
    notification.addAction(muteAction);

    if (SystemInfo.isMac) {
      final AnAction changeSystemSettings = DumbAwareAction.create("Change system settings", e -> {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          final GeneralCommandLine cmdLine =
                  new GeneralCommandLine("osascript", "-e", "tell application \"System Preferences\"", "-e", "set the current pane to pane id \"com.apple.preference.keyboard\"", "-e",
                                         "reveal anchor \"shortcutsTab\" of pane id \"com.apple.preference.keyboard\"", "-e", "activate", "-e", "end tell");
          try {
            ExecUtil.execAndGetOutput(cmdLine);
            // NOTE: we can't detect OS-settings changes
            // but we can try to schedule check conflicts (and expire notification if necessary)
          }
          catch (ExecutionException ex) {
            LOG.error(ex);
          }
        });
      });
      notification.addAction(changeSystemSettings);
    }

    myNotifiedActions.add(actionId);
    notification.notify(null);
  }

  private static Class ourShkClass;
  private static Method ourMethodGetDescription;
  private static Method ourMethodReadSystemHotkeys;

  private static
  @Nonnull
  String getDescription(@Nonnull AWTKeyStroke systemHotkey) {
    if (ourShkClass == null) ourShkClass = ReflectionUtil.forName("java.awt.desktop.SystemHotkey");
    if (ourShkClass == null) return ourUnknownSysAction;

    if (ourMethodGetDescription == null) ourMethodGetDescription = ReflectionUtil.getMethod(ourShkClass, "getDescription");
    String result = null;
    try {
      result = (String)ourMethodGetDescription.invoke(systemHotkey);
    }
    catch (Throwable e) {
      Logger.getInstance(SystemShortcuts.class).error(e);
    }
    return result == null ? ourUnknownSysAction : result;
  }

  private static final boolean DEBUG_SYSTEM_SHORTCUTS = Boolean.getBoolean("debug.system.shortcuts");

  private void readSystem() {
    myKeyStroke2SysShortcut.clear();

    if (!SystemInfo.isMac || !SystemInfo.isJetBrainsJvm) return;

    try {
      if (!Registry.is("read.system.shortcuts")) return;

      if (ourShkClass == null) ourShkClass = ReflectionUtil.forName("java.awt.desktop.SystemHotkey");
      if (ourShkClass == null) return;

      if (ourMethodReadSystemHotkeys == null) ourMethodReadSystemHotkeys = ReflectionUtil.getMethod(ourShkClass, "readSystemHotkeys");
      if (ourMethodReadSystemHotkeys == null) return;

      List<AWTKeyStroke> all = (List<AWTKeyStroke>)ourMethodReadSystemHotkeys.invoke(ourShkClass);
      if (all == null || all.isEmpty()) return;

      String debugInfo = "";
      for (AWTKeyStroke shk : all) {
        if (shk.getModifiers() == 0) {
          //System.out.println("Skip system shortcut [without modifiers]: " + shk);
          continue;
        }
        if (shk.getKeyChar() == KeyEvent.CHAR_UNDEFINED && shk.getKeyCode() == KeyEvent.VK_UNDEFINED) {
          //System.out.println("Skip system shortcut [undefined key]: " + shk);
          continue;
        }
        if ("Move focus to the next window in application".equals(getDescription(shk))) {
          // Skip this shortcut because it handled in IDE-side
          // see: JBR-1515 Regression test jb/sun/awt/macos/MoveFocusShortcutTest.java fails on macOS  (Now we prevent Mac OS from handling the shortcut. We can enumerate windows on IDE level.)
          continue;
        }

        KeyStroke sysKS;
        if (shk.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
          final int keyCode = KeyEvent.getExtendedKeyCodeForChar(shk.getKeyChar());
          if (keyCode == KeyEvent.VK_UNDEFINED) {
            //System.out.println("Skip system shortcut [undefined key]: " + shk);
            continue;
          }
          sysKS = KeyStroke.getKeyStroke(keyCode, shk.getModifiers());
        }
        else sysKS = KeyStroke.getKeyStroke(shk.getKeyCode(), shk.getModifiers());

        myKeyStroke2SysShortcut.put(sysKS, shk);

        if (DEBUG_SYSTEM_SHORTCUTS) {
          debugInfo += shk.toString() + ";\n";
        }
      }
      if (DEBUG_SYSTEM_SHORTCUTS) {
        Logger.getInstance(SystemShortcuts.class).info("system shortcuts:\n" + debugInfo);
      }
    }
    catch (Throwable e) {
      Logger.getInstance(SystemShortcuts.class).debug(e);
    }
  }

  private static class MuteConflictsSettings {
    private static final String MUTED_ACTIONS_KEY = "muted.system.shortcut.conflicts.actions";
    private
    @Nonnull
    Set<String> myMutedActions;

    void init() {
      if (myMutedActions != null) return;
      myMutedActions = new HashSet<>();
      final String[] muted = PropertiesComponent.getInstance().getValues(MUTED_ACTIONS_KEY);
      if (muted != null) {
        Collections.addAll(myMutedActions, muted);
      }
    }

    void addMutedAction(@Nonnull String actId) {
      init();
      myMutedActions.add(actId);
      PropertiesComponent.getInstance().setValues(MUTED_ACTIONS_KEY, ArrayUtilRt.toStringArray(myMutedActions));
    }

    void removeMutedAction(@Nonnull String actId) {
      init();
      myMutedActions.remove(actId);
      PropertiesComponent.getInstance().setValues(MUTED_ACTIONS_KEY, ArrayUtilRt.toStringArray(myMutedActions));
    }

    public boolean isMutedAction(@Nonnull String actionId) {
      init();
      return myMutedActions.contains(actionId);
    }
  }
}
