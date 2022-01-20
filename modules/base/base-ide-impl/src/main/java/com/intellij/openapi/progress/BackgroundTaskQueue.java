/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.openapi.progress;

import consulo.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import consulo.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.QueueProcessor;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.progress.ProgressIndicator;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.function.BooleanSupplier;

import static com.intellij.util.concurrency.QueueProcessor.ThreadToUse;

/**
 * Runs backgroundable tasks one by one.
 * To add a task to the queue use {@link #run(Task.Backgroundable)}
 * BackgroundTaskQueue may have a title - this title will be used if the task which is currently running doesn't have a title.
 */
public class BackgroundTaskQueue {
  @Nonnull
  protected final String myTitle;
  @Nonnull
  protected final QueueProcessor<TaskData> myProcessor;

  @Nonnull
  private final Object TEST_TASK_LOCK = new Object();
  private volatile boolean myForceAsyncInTests = false;

  public BackgroundTaskQueue(@Nullable Project project, @Nonnull String title) {
    myTitle = title;

    BooleanSupplier disposeCondition = project != null ? project.getDisposed() : ApplicationManager.getApplication().getDisposed();
    myProcessor = new QueueProcessor<>(TaskData::consume, true, ThreadToUse.AWT, disposeCondition);
  }

  public void clear() {
    myProcessor.clear();
  }

  public boolean isEmpty() {
    return myProcessor.isEmpty();
  }

  public void waitForTasksToFinish() {
    myProcessor.waitFor();
  }

  public void run(@Nonnull Task.Backgroundable task) {
    run(task, null, null);
  }

  public void run(@Nonnull Task.Backgroundable task, @Nullable ModalityState modalityState, @Nullable ProgressIndicator indicator) {
    BackgroundableTaskData taskData = new BackgroundableTaskData(task, modalityState, indicator);
    if (!myForceAsyncInTests && ApplicationManager.getApplication().isUnitTestMode()) {
      runTaskInCurrentThread(taskData);
    }
    else {
      myProcessor.add(taskData, modalityState);
    }
  }


  @TestOnly
  public void setForceAsyncInTests(boolean value, @Nullable Disposable disposable) {
    myForceAsyncInTests = value;
    if (disposable != null) {
      Disposer.register(disposable, new Disposable() {
        @Override
        public void dispose() {
          myForceAsyncInTests = false;
        }
      });
    }
  }

  private void runTaskInCurrentThread(@Nonnull BackgroundableTaskData data) {
    Task.Backgroundable task = data.myTask;

    ProgressIndicator indicator = data.myIndicator;
    if (indicator == null) indicator = new EmptyProgressIndicator();

    ModalityState modalityState = data.myModalityState;
    if (modalityState == null) modalityState = ModalityState.NON_MODAL;

    ProgressManagerImpl pm = (ProgressManagerImpl)ProgressManager.getInstance();

    // prohibit simultaneous execution from different threads
    synchronized (TEST_TASK_LOCK) {
      pm.runProcessWithProgressInCurrentThread(task, indicator, modalityState);
    }
  }

  protected interface TaskData extends Consumer<Runnable> {
  }

  protected class BackgroundableTaskData implements TaskData {
    @Nonnull
    private final Task.Backgroundable myTask;
    @Nullable
    private final ModalityState myModalityState;
    @Nullable
    private final ProgressIndicator myIndicator;

    public BackgroundableTaskData(@Nonnull Task.Backgroundable task,
                                  @Nullable ModalityState modalityState,
                                  @Nullable ProgressIndicator indicator) {
      myTask = task;
      myModalityState = modalityState;
      myIndicator = indicator;
    }

    @Override
    public void consume(@Nonnull Runnable continuation) {
      Task.Backgroundable task = myTask;
      ProgressIndicator indicator = myIndicator;
      if (indicator == null) {
        if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
          indicator = new EmptyProgressIndicator();
        }
        else {
          // BackgroundableProcessIndicator should be created from EDT
          indicator = new BackgroundableProcessIndicator(task);
        }
      }

      ModalityState modalityState = myModalityState;
      if (modalityState == null) modalityState = ModalityState.NON_MODAL;

      if (StringUtil.isEmptyOrSpaces(task.getTitle())) {
        task.setTitle(myTitle);
      }

      boolean synchronous = (task.isHeadless() && !myForceAsyncInTests) ||
                            (task.isConditionalModal() && !task.shouldStartInBackground());

      ProgressManagerImpl pm = (ProgressManagerImpl)ProgressManager.getInstance();
      if (synchronous) {
        try {
          pm.runProcessWithProgressSynchronously(task);
        }
        finally {
          continuation.run();
        }
      }
      else {
        pm.runProcessWithProgressAsynchronously(task, indicator, continuation, modalityState);
      }
    }
  }
}
