/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 30.11.2006
 * Time: 18:12:47
 */
package com.intellij.openapi.vcs.changes.committed;

import consulo.application.ApplicationManager;
import consulo.project.Project;
import com.intellij.openapi.ui.MessageType;
import consulo.disposer.Disposer;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import consulo.virtualFileSystem.VirtualFile;
import consulo.component.messagebus.MessageBus;
import consulo.component.messagebus.MessageBusConnection;
import javax.annotation.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.BooleanSupplier;

public class CommittedChangesViewManager implements ChangesViewContentProvider {
  private final ProjectLevelVcsManager myVcsManager;
  private final MessageBus myBus;
  private MessageBusConnection myConnection;
  private CommittedChangesPanel myComponent;
  private final Project myProject;
  private final VcsListener myVcsListener = new MyVcsListener();

  public CommittedChangesViewManager(final Project project, final ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myVcsManager = vcsManager;
    myBus = project.getMessageBus();
  }

  private void updateChangesContent() {
    final CommittedChangesProvider provider = CommittedChangesCache.getInstance(myProject).getProviderForProject();
    if (provider == null) return;

    if (myComponent == null) {
      myComponent = new CommittedChangesPanel(myProject, provider, provider.createDefaultSettings(), null, null);
      myConnection.subscribe(VcsConfigurationChangeListener.BRANCHES_CHANGED, new VcsConfigurationChangeListener.Notification() {
        public void execute(final Project project, final VirtualFile vcsRoot) {
          sendUpdateCachedListsMessage(vcsRoot);
        }
      });
    }
    else {
      myComponent.setProvider(provider);
      // called from listener to notification of vcs root changes
      sendUpdateCachedListsMessage(null);
    }
  }

  private void sendUpdateCachedListsMessage(final VirtualFile vcsRoot) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        myComponent.passCachedListsToListener(myBus.syncPublisher(VcsConfigurationChangeListener.BRANCHES_CHANGED_RESPONSE),
                                              myProject, vcsRoot);
      }
    }, new BooleanSupplier() {
      @Override
      public boolean getAsBoolean() {
        return (! myProject.isOpen()) || myProject.isDisposed() || myComponent == null;
      }
    });
  }

  public JComponent initContent() {
    myVcsManager.addVcsListener(myVcsListener);
    myConnection = myBus.connect();
    myConnection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new MyCommittedChangesListener());
    updateChangesContent();
    myComponent.refreshChanges(true);
    return myComponent;
  }

  public void disposeContent() {
    myVcsManager.removeVcsListener(myVcsListener);
    myConnection.disconnect();
    Disposer.dispose(myComponent);
    myComponent = null;
  }

  private class MyVcsListener implements VcsListener {
    public void directoryMappingChanged() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (!myProject.isDisposed()) {
            updateChangesContent();
          }
        }
      });
    }
  }

  private class MyCommittedChangesListener extends CommittedChangesAdapter {
    public void changesLoaded(RepositoryLocation location, List<CommittedChangeList> changes) {
      presentationChanged();
    }

    @Override
    public void presentationChanged() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myComponent != null && !myProject.isDisposed()) {
            myComponent.refreshChanges(true);
          }
        }
      });
    }

    public void refreshErrorStatusChanged(@Nullable final VcsException lastError) {
      if (lastError != null) {
        VcsBalloonProblemNotifier.showOverChangesView(myProject, lastError.getMessage(), MessageType.ERROR);
      }
    }
  }
}
