// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.openapi.vcs.roots;

import com.google.common.annotations.VisibleForTesting;
import consulo.ui.ex.action.ActionsBundle;
import consulo.project.ui.notification.Notification;
import consulo.ide.impl.idea.notification.NotificationAction;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.ide.impl.idea.openapi.diagnostic.Logger;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.ide.impl.idea.openapi.progress.util.BackgroundTaskUtil;
import consulo.project.Project;
import consulo.module.content.ProjectFileIndex;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.application.util.registry.Registry;
import consulo.ide.impl.idea.openapi.util.text.StringUtil;
import consulo.ide.impl.idea.openapi.vcs.*;
import consulo.vcs.change.ChangeListManager;
import consulo.vcs.ProjectLevelVcsManager;
import consulo.vcs.VcsBundle;
import consulo.vcs.VcsConfiguration;
import consulo.vcs.VcsDirectoryMapping;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.ide.impl.idea.util.Function;
import consulo.vcs.util.VcsUtil;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static consulo.ide.impl.idea.openapi.util.io.FileUtil.toSystemDependentName;
import static consulo.ide.impl.idea.openapi.util.text.StringUtil.escapeXmlEntities;
import static consulo.ide.impl.idea.openapi.vcs.VcsNotificationIdsHolder.ROOTS_INVALID;
import static consulo.ide.impl.idea.openapi.vcs.VcsNotificationIdsHolder.ROOTS_REGISTERED;
import static consulo.ide.impl.idea.openapi.vcs.VcsRootError.Type.UNREGISTERED_ROOT;
import static consulo.ide.impl.idea.util.containers.ContainerUtil.*;
import static consulo.ui.ex.awt.UIUtil.BR;

/**
 * Searches for Vcs roots problems via {@link VcsRootErrorsFinder} and notifies about them.
 */
public final class VcsRootProblemNotifier {
  private static final Logger LOG = Logger.getInstance(VcsRootProblemNotifier.class);

  @Nonnull
  private final Project myProject;
  @Nonnull
  private final VcsConfiguration mySettings;
  @Nonnull
  private final ProjectLevelVcsManager myVcsManager;
  @Nonnull
  private final ChangeListManager myChangeListManager;
  @Nonnull
  private final ProjectFileIndex myProjectFileIndex;

  // unregistered roots reported during this session but not explicitly ignored
  @Nonnull
  private final Set<String> myReportedUnregisteredRoots;

  @Nullable
  private Notification myNotification;
  @Nonnull
  private final Object NOTIFICATION_LOCK = new Object();

  @Nonnull
  private final Function<VcsRootError, String> ROOT_TO_PRESENTABLE = rootError -> getPresentableMapping(rootError.getMapping());

  public static VcsRootProblemNotifier createInstance(@Nonnull Project project) {
    return new VcsRootProblemNotifier(project);
  }

  private VcsRootProblemNotifier(@Nonnull Project project) {
    myProject = project;
    mySettings = VcsConfiguration.getInstance(myProject);
    myChangeListManager = ChangeListManager.getInstance(project);
    myProjectFileIndex = ProjectFileIndex.SERVICE.getInstance(myProject);
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myReportedUnregisteredRoots = new HashSet<>();
  }

  public void rescanAndNotifyIfNeeded() {
    Collection<VcsRootError> errors = scan();
    if (errors.isEmpty()) {
      synchronized (NOTIFICATION_LOCK) {
        expireNotification();
      }
      return;
    }
    LOG.debug("Following errors detected: " + errors);

    Collection<VcsRootError> importantUnregisteredRoots = getImportantUnregisteredMappings(errors);
    Collection<VcsRootError> invalidRoots = getInvalidRoots(errors);

    String title;
    String description;
    NotificationAction[] notificationActions;

    if (Registry.is("vcs.root.auto.add") && !areThereExplicitlyIgnoredRoots(errors)) {
      if (invalidRoots.isEmpty() && importantUnregisteredRoots.isEmpty()) return;

      LOG.info("Auto-registered following mappings: " + importantUnregisteredRoots);
      addMappings(importantUnregisteredRoots);

      // Register the single root equal to the project dir silently, without any notification
      if (invalidRoots.isEmpty() && importantUnregisteredRoots.size() == 1) {
        VcsRootError rootError = Objects.requireNonNull(getFirstItem(importantUnregisteredRoots));
        if (FileUtil.pathsEqual(rootError.getMapping().getDirectory(), myProject.getBasePath())) {
          return;
        }
      }

      // Don't display the notification about registered roots unless configured to do so (and unless there are invalid roots)
      if (invalidRoots.isEmpty() && !Registry.is("vcs.root.auto.add.nofity")) {
        return;
      }

      title = makeTitle(importantUnregisteredRoots, invalidRoots, true);
      description = makeDescription(importantUnregisteredRoots, invalidRoots);
      notificationActions = new NotificationAction[]{getConfigureNotificationAction()};
    }
    else {
      // Don't report again, if these roots were already reported
      List<String> unregRootPaths = map(importantUnregisteredRoots, rootError -> rootError.getMapping().getDirectory());
      if (invalidRoots.isEmpty() && (importantUnregisteredRoots.isEmpty() || myReportedUnregisteredRoots.containsAll(unregRootPaths))) {
        return;
      }
      myReportedUnregisteredRoots.addAll(unregRootPaths);

      title = makeTitle(importantUnregisteredRoots, invalidRoots, false);
      description = makeDescription(importantUnregisteredRoots, invalidRoots);

      NotificationAction enableIntegration = NotificationAction
              .create(VcsBundle.message("action.NotificationAction.VcsRootProblemNotifier.text.enable.integration"), (event, notification) -> addMappings(importantUnregisteredRoots));
      NotificationAction ignoreAction = NotificationAction.create(VcsBundle.message("action.NotificationAction.VcsRootProblemNotifier.text.ignore"), (event, notification) -> {
        mySettings.addIgnoredUnregisteredRoots(map(importantUnregisteredRoots, rootError -> rootError.getMapping().getDirectory()));
        notification.expire();
      });
      notificationActions = new NotificationAction[]{enableIntegration, getConfigureNotificationAction(), ignoreAction};
    }

    synchronized (NOTIFICATION_LOCK) {
      expireNotification();
      VcsNotifier notifier = VcsNotifier.getInstance(myProject);

      myNotification = invalidRoots.isEmpty()
                       ? notifier.notifyMinorInfo(ROOTS_REGISTERED, title, description, notificationActions)
                       : notifier.notifyError(ROOTS_INVALID, title, description, getConfigureNotificationAction());
    }
  }

  @Nonnull
  private NotificationAction getConfigureNotificationAction() {
    return NotificationAction.create(VcsBundle.message("action.NotificationAction.VcsRootProblemNotifier.text.configure"), (event, notification) -> {
      if (!myProject.isDisposed()) {
        ShowSettingsUtil.getInstance().showSettingsDialog(myProject, ActionsBundle.message("group.VcsGroup.text"));

        BackgroundTaskUtil.executeOnPooledThread(myProject, () -> {
          Collection<VcsRootError> errorsAfterPossibleFix = new VcsRootProblemNotifier(myProject).scan();
          if (errorsAfterPossibleFix.isEmpty() && !notification.isExpired()) {
            notification.expire();
          }
        });
      }
    });
  }

  private void addMappings(Collection<? extends VcsRootError> importantUnregisteredRoots) {
    List<VcsDirectoryMapping> mappings = myVcsManager.getDirectoryMappings();
    for (VcsRootError root : importantUnregisteredRoots) {
      mappings = VcsUtil.addMapping(mappings, root.getMapping());
    }
    myVcsManager.setDirectoryMappings(mappings);
  }

  private boolean isUnderOrAboveProjectDir(@Nonnull VcsDirectoryMapping mapping) {
    String projectDir = Objects.requireNonNull(myProject.getBasePath());
    return mapping.isDefaultMapping() || FileUtil.isAncestor(projectDir, mapping.getDirectory(), false) || FileUtil.isAncestor(mapping.getDirectory(), projectDir, false);
  }

  private boolean isIgnoredOrExcludedPath(@Nonnull VcsDirectoryMapping mapping) {
    if (mapping.isDefaultMapping()) return false;
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(mapping.getDirectory());
    return file != null && (myChangeListManager.isIgnoredFile(file) || ReadAction.compute(() -> myProjectFileIndex.isExcluded(file)));
  }

  private boolean isExplicitlyIgnoredPath(@Nonnull VcsDirectoryMapping mapping) {
    if (mapping.isDefaultMapping()) return false;
    return mySettings.isIgnoredUnregisteredRoot(mapping.getDirectory());
  }

  private void expireNotification() {
    if (myNotification != null) {
      final Notification notification = myNotification;
      ApplicationManager.getApplication().invokeLater(notification::expire);

      myNotification = null;
    }
  }

  @Nonnull
  private Collection<VcsRootError> scan() {
    return new VcsRootErrorsFinder(myProject).find();
  }

  @Nonnull
  private String makeDescription(@Nonnull Collection<? extends VcsRootError> unregisteredRoots, @Nonnull Collection<? extends VcsRootError> invalidRoots) {
    @Nls StringBuilder description = new StringBuilder();
    if (!invalidRoots.isEmpty()) {
      if (invalidRoots.size() == 1) {
        VcsRootError rootError = invalidRoots.iterator().next();
        String vcsName = rootError.getMapping().getVcs();
        description.append(getInvalidRootDescriptionItem(rootError, vcsName));
      }
      else {
        description.append(VcsBundle.message("roots.the.following.directories.are.registered.as.vcs.roots.but.they.are.not")).append(BR).append(joinRootsForPresentation(invalidRoots));
      }
      description.append(BR);
    }

    if (!unregisteredRoots.isEmpty()) {
      if (unregisteredRoots.size() == 1) {
        VcsRootError unregisteredRoot = unregisteredRoots.iterator().next();
        description.append(ROOT_TO_PRESENTABLE.fun(unregisteredRoot));
      }
      else {
        description.append(joinRootsForPresentation(unregisteredRoots));
      }
    }
    return description.toString();
  }

  @VisibleForTesting
  @Nonnull
  String getInvalidRootDescriptionItem(@Nonnull VcsRootError rootError, @Nonnull String vcsName) {
    return VcsBundle.message("roots.notification.content.directory.registered.as.root.but.no.repositories.were.found.there", ROOT_TO_PRESENTABLE.fun(rootError), vcsName);
  }

  @Nonnull
  private String joinRootsForPresentation(@Nonnull Collection<? extends VcsRootError> errors) {
    List<? extends VcsRootError> sortedRoots = sorted(errors, (root1, root2) -> {
      if (root1.getMapping().isDefaultMapping()) return -1;
      if (root2.getMapping().isDefaultMapping()) return 1;
      return root1.getMapping().getDirectory().compareTo(root2.getMapping().getDirectory());
    });
    return StringUtil.join(sortedRoots, ROOT_TO_PRESENTABLE, BR);
  }

  @Nonnull
  private static String makeTitle(@Nonnull Collection<? extends VcsRootError> unregisteredRoots, @Nonnull Collection<? extends VcsRootError> invalidRoots, boolean rootsAlreadyAdded) {
    String title;
    if (unregisteredRoots.isEmpty()) {
      title = VcsBundle.message("roots.notification.title.invalid.vcs.root.choice.mapping.mappings", invalidRoots.size());
    }
    else if (invalidRoots.isEmpty()) {
      String vcs = getVcsName(unregisteredRoots);
      title = rootsAlreadyAdded
              ? VcsBundle.message("roots.notification.title.vcs.name.integration.enabled", vcs)
              : VcsBundle.message("notification.title.vcs.name.repository.repositories.found", vcs, unregisteredRoots.size());
    }
    else {
      title = VcsBundle.message("roots.notification.title.vcs.root.configuration.problems");
    }
    return title;
  }

  private static String getVcsName(Collection<? extends VcsRootError> roots) {
    String result = null;
    for (VcsRootError root : roots) {
      String vcsName = root.getMapping().getVcs();
      if (result == null) {
        result = vcsName;
      }
      else if (!result.equals(vcsName)) {
        return VcsBundle.message("vcs.generic.name");
      }
    }
    return result;
  }

  @Nonnull
  private List<VcsRootError> getImportantUnregisteredMappings(@Nonnull Collection<? extends VcsRootError> errors) {
    return filter(errors, error -> {
      VcsDirectoryMapping mapping = error.getMapping();
      return error.getType() == UNREGISTERED_ROOT && isUnderOrAboveProjectDir(mapping) && !isIgnoredOrExcludedPath(mapping) && !isExplicitlyIgnoredPath(mapping);
    });
  }

  private boolean areThereExplicitlyIgnoredRoots(Collection<? extends VcsRootError> allErrors) {
    return exists(allErrors, it -> it.getType() == UNREGISTERED_ROOT && isExplicitlyIgnoredPath(it.getMapping()));
  }

  @Nonnull
  private static Collection<VcsRootError> getInvalidRoots(@Nonnull Collection<? extends VcsRootError> errors) {
    return filter(errors, error -> error.getType() == VcsRootError.Type.EXTRA_MAPPING);
  }


  @VisibleForTesting
  @Nonnull
  String getPresentableMapping(@Nonnull VcsDirectoryMapping directoryMapping) {
    if (directoryMapping.isDefaultMapping()) return directoryMapping.toString();

    return getPresentableMapping(directoryMapping.getDirectory());
  }

  @VisibleForTesting
  @Nonnull
  String getPresentableMapping(@Nonnull String mapping) {
    String presentablePath = null;
    String projectDir = myProject.getBasePath();
    if (projectDir != null && FileUtil.isAncestor(projectDir, mapping, true)) {
      String relativePath = FileUtil.getRelativePath(projectDir, mapping, '/');
      if (relativePath != null) {
        presentablePath = toSystemDependentName(VcsBundle.message("label.relative.project.path.presentation", relativePath));
      }
    }
    if (presentablePath == null) {
      presentablePath = FileUtil.getLocationRelativeToUserHome(toSystemDependentName(mapping));
    }
    return escapeXmlEntities(presentablePath);
  }
}
