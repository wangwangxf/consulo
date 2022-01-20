/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution;

import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.component.persist.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import consulo.project.Project;
import com.intellij.util.containers.ContainerUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdom.Element;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@State(name = "ExecutionTargetManager", storages = @Storage(file = StoragePathMacros.WORKSPACE_FILE))
@Singleton
public class ExecutionTargetManagerImpl extends ExecutionTargetManager implements PersistentStateComponent<Element> {
  @Nonnull
  private final Application myApplication;
  @Nonnull
  private final Project myProject;
  @Nonnull
  private final Object myActiveTargetLock = new Object();
  @Nullable
  private ExecutionTarget myActiveTarget;

  @Nullable
  private String mySavedActiveTargetId;

  @Inject
  public ExecutionTargetManagerImpl(@Nonnull Application application, @Nonnull Project project) {
    myApplication = application;
    myProject = project;

    project.getMessageBus().connect().subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
      @Override
      public void runConfigurationChanged(@Nonnull RunnerAndConfigurationSettings settings) {
        if (settings == RunManager.getInstance(myProject).getSelectedConfiguration()) {
          updateActiveTarget(settings);
        }
      }

      @Override
      public void runConfigurationSelected(@Nullable RunnerAndConfigurationSettings selected) {
        ReadAction.run(() -> updateActiveTarget(selected));
      }
    });
  }

  @Override
  public Element getState() {
    synchronized (myActiveTargetLock) {
      Element state = new Element("state");

      String id = myActiveTarget == null ? mySavedActiveTargetId : myActiveTarget.getId();
      if (id != null) state.setAttribute("SELECTED_TARGET", id);
      return state;
    }
  }

  @Override
  public void loadState(Element state) {
    synchronized (myActiveTargetLock) {
      if (myActiveTarget == null && mySavedActiveTargetId == null) {
        mySavedActiveTargetId = state.getAttributeValue("SELECTED_TARGET");
      }
    }
  }

  @RequiredReadAction
  @Nonnull
  @Override
  public ExecutionTarget getActiveTarget() {
    myApplication.assertReadAccessAllowed();
    synchronized (myActiveTargetLock) {
      if (myActiveTarget == null) {
        updateActiveTarget();
      }
      return myActiveTarget;
    }
  }

  @RequiredUIAccess
  @Override
  public void setActiveTarget(@Nonnull ExecutionTarget target) {
    myApplication.assertIsDispatchThread();
    synchronized (myActiveTargetLock) {
      updateActiveTarget(RunManager.getInstance(myProject).getSelectedConfiguration(), target);
    }
  }

  private void updateActiveTarget() {
    updateActiveTarget(RunManager.getInstance(myProject).getSelectedConfiguration());
  }

  private void updateActiveTarget(@Nullable RunnerAndConfigurationSettings settings) {
    updateActiveTarget(settings, null);
  }

  private void updateActiveTarget(@Nullable RunnerAndConfigurationSettings settings, @Nullable ExecutionTarget toSelect) {
    List<ExecutionTarget> suitable = settings == null ? Collections.singletonList(DefaultExecutionTarget.INSTANCE) : getTargetsFor(settings);
    ExecutionTarget toNotify = null;
    synchronized (myActiveTargetLock) {
      if (toSelect == null) toSelect = myActiveTarget;

      int index = -1;
      if (toSelect != null) {
        index = suitable.indexOf(toSelect);
      }
      else if (mySavedActiveTargetId != null) {
        for (int i = 0, size = suitable.size(); i < size; i++) {
          if (suitable.get(i).getId().equals(mySavedActiveTargetId)) {
            index = i;
            break;
          }
        }
      }
      toNotify = doSetActiveTarget(index >= 0 ? suitable.get(index) : ContainerUtil.getFirstItem(suitable, DefaultExecutionTarget.INSTANCE));
    }

    if (toNotify != null) {
      myProject.getMessageBus().syncPublisher(TOPIC).activeTargetChanged(toNotify);
    }
  }

  @Nullable
  private ExecutionTarget doSetActiveTarget(@Nonnull ExecutionTarget newTarget) {
    mySavedActiveTargetId = null;

    ExecutionTarget prev = myActiveTarget;
    myActiveTarget = newTarget;
    if (prev != null && !prev.equals(myActiveTarget)) {
      return myActiveTarget;
    }
    return null;
  }

  @RequiredReadAction
  @Nonnull
  @Override
  public List<ExecutionTarget> getTargetsFor(@Nullable RunnerAndConfigurationSettings settings) {
    myApplication.assertReadAccessAllowed();
    if (settings == null) return Collections.emptyList();

    List<ExecutionTarget> result = new ArrayList<>();
    for (ExecutionTargetProvider eachTargetProvider : ExecutionTargetProvider.EXTENSION_NAME.getExtensionList()) {
      for (ExecutionTarget eachTarget : eachTargetProvider.getTargets(myProject, settings)) {
        if (canRun(settings, eachTarget)) result.add(eachTarget);
      }
    }
    return Collections.unmodifiableList(result);
  }

  @RequiredUIAccess
  @Override
  public void update() {
    myApplication.assertIsDispatchThread();
    updateActiveTarget();
  }
}
