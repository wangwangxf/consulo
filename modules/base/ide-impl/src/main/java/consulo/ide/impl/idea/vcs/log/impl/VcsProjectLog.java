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
package consulo.ide.impl.idea.vcs.log.impl;

import consulo.ide.impl.idea.ide.caches.CachesInvalidator;
import consulo.application.ApplicationManager;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import consulo.project.startup.IdeaStartupActivity;
import consulo.disposer.Disposer;
import consulo.ide.impl.idea.openapi.vcs.CalledInAny;
import consulo.ide.impl.idea.openapi.vcs.CalledInAwt;
import consulo.ide.impl.idea.openapi.vcs.ProjectLevelVcsManager;
import consulo.ide.impl.idea.openapi.vcs.VcsRoot;
import consulo.component.messagebus.MessageBus;
import consulo.component.messagebus.MessageBusConnection;
import consulo.component.messagebus.TopicImpl;
import consulo.ide.impl.idea.vcs.log.data.VcsLogData;
import consulo.ide.impl.idea.vcs.log.data.VcsLogTabsProperties;
import consulo.ide.impl.idea.vcs.log.ui.VcsLogPanel;
import consulo.ide.impl.idea.vcs.log.ui.VcsLogUiImpl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

@Singleton
public class VcsProjectLog {
  public static final TopicImpl<ProjectLogListener> VCS_PROJECT_LOG_CHANGED =
          TopicImpl.create("Project Vcs Log Created or Disposed", ProjectLogListener.class);
  @Nonnull
  private final Project myProject;
  @Nonnull
  private final MessageBus myMessageBus;
  @Nonnull
  private final VcsLogTabsProperties myUiProperties;

  @Nonnull
  private final LazyVcsLogManager myLogManager = new LazyVcsLogManager();
  private volatile VcsLogUiImpl myUi;

  @Inject
  public VcsProjectLog(@Nonnull Project project, @Nonnull VcsLogTabsProperties uiProperties) {
    myProject = project;
    myMessageBus = project.getMessageBus();
    myUiProperties = uiProperties;
  }

  @Nullable
  public VcsLogData getDataManager() {
    VcsLogManager cached = myLogManager.getCached();
    if (cached == null) return null;
    return cached.getDataManager();
  }

  @Nonnull
  private Collection<VcsRoot> getVcsRoots() {
    return Arrays.asList(ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots());
  }

  @Nonnull
  public JComponent initMainLog(@Nonnull String contentTabName) {
    myUi = myLogManager.getValue().createLogUi(VcsLogTabsProperties.MAIN_LOG_ID, contentTabName, null);
    return new VcsLogPanel(myLogManager.getValue(), myUi);
  }

  /**
   * The instance of the {@link VcsLogUiImpl} or null if the log was not initialized yet.
   */
  @Nullable
  public VcsLogUiImpl getMainLogUi() {
    return myUi;
  }

  @Nullable
  public VcsLogManager getLogManager() {
    return myLogManager.getCached();
  }

  @CalledInAny
  private void recreateLog() {
    ApplicationManager.getApplication().invokeLater(() -> {
      disposeLog();

      if (hasDvcsRoots()) {
        createLog();
      }
    });
  }

  @CalledInAwt
  private void disposeLog() {
    myUi = null;
    myLogManager.drop();
  }

  @CalledInAwt
  public void createLog() {
    VcsLogManager logManager = myLogManager.getValue();

    if (logManager.isLogVisible()) {
      logManager.scheduleInitialization();
    }
    else if (PostponableLogRefresher.keepUpToDate()) {
      VcsLogCachesInvalidator invalidator = CachesInvalidator.EP_NAME.findExtension(VcsLogCachesInvalidator.class);
      if (invalidator.isValid()) {
        HeavyAwareExecutor.executeOutOfHeavyProcessLater(logManager::scheduleInitialization, 5000);
      }
    }
  }

  private boolean hasDvcsRoots() {
    return !VcsLogManager.findLogProviders(getVcsRoots(), myProject).isEmpty();
  }

  public static VcsProjectLog getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, VcsProjectLog.class);
  }

  private class LazyVcsLogManager {
    @Nullable private VcsLogManager myValue;

    @Nonnull
    @CalledInAwt
    public synchronized VcsLogManager getValue() {
      if (myValue == null) {
        myValue = compute();
        myMessageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logCreated();
      }
      return myValue;
    }

    @Nonnull
    @CalledInAwt
    protected synchronized VcsLogManager compute() {
      return new VcsLogManager(myProject, myUiProperties, getVcsRoots(), false, VcsProjectLog.this::recreateLog);
    }

    @CalledInAwt
    public synchronized void drop() {
      if (myValue != null) {
        myMessageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logDisposed();
        Disposer.dispose(myValue);
      }
      myValue = null;
    }

    @Nullable
    public synchronized VcsLogManager getCached() {
      return myValue;
    }
  }

  public static class InitLogStartupActivity implements IdeaStartupActivity {
    @Override
    public void runActivity(@Nonnull Project project) {
      if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) return;

      VcsProjectLog projectLog = getInstance(project);

      MessageBusConnection connection = project.getMessageBus().connect(project);
      connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, projectLog::recreateLog);
      if (projectLog.hasDvcsRoots()) {
        ApplicationManager.getApplication().invokeLater(projectLog::createLog);
      }
    }
  }

  public interface ProjectLogListener {
    @CalledInAwt
    void logCreated();

    @CalledInAwt
    void logDisposed();
  }
}
