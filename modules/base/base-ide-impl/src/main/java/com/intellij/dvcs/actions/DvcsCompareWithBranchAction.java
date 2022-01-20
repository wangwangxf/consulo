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
package com.intellij.dvcs.actions;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import consulo.logging.Logger;
import consulo.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import consulo.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.vcsUtil.VcsUtil;
import javax.annotation.Nonnull;

import consulo.ui.annotation.RequiredUIAccess;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Compares selected file/folder with itself in another branch.
 */
public abstract class DvcsCompareWithBranchAction<T extends Repository> extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(DvcsCompareWithBranchAction.class);

  @RequiredUIAccess
  @Override
  public void actionPerformed(@Nonnull AnActionEvent event) {
    Project project = event.getRequiredData(CommonDataKeys.PROJECT);
    VirtualFile file = getAffectedFile(event);
    T repository = ObjectUtils.assertNotNull(getRepositoryManager(project).getRepositoryForFile(file));
    assert !repository.isFresh();
    String currentBranchName = repository.getCurrentBranchName();
    String presentableRevisionName = currentBranchName;
    if (currentBranchName == null) {
      String currentRevision = ObjectUtils.assertNotNull(repository.getCurrentRevision());
      presentableRevisionName = DvcsUtil.getShortHash(currentRevision);
    }
    List<String> branchNames = getBranchNamesExceptCurrent(repository);

    JBList list = new JBList(branchNames);
    JBPopupFactory.getInstance().createListPopupBuilder(list).setTitle("Select branch to compare")
            .setItemChoosenCallback(new OnBranchChooseRunnable(project, file, presentableRevisionName, list)).setAutoselectOnMouseMove(true)
            .setFilteringEnabled(new Function<Object, String>() {
              @Override
              public String fun(Object o) {
                return o.toString();
              }
            }).createPopup().showCenteredInCurrentWindow(project);
  }

  @Nonnull
  protected abstract List<String> getBranchNamesExceptCurrent(@Nonnull T repository);

  private static VirtualFile getAffectedFile(@Nonnull AnActionEvent event) {
    final VirtualFile[] vFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    assert vFiles != null && vFiles.length == 1 && vFiles[0] != null : "Illegal virtual files selected: " + Arrays.toString(vFiles);
    return vFiles[0];
  }

  @RequiredUIAccess
  @Override
  public void update(@Nonnull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    VirtualFile file = VcsUtil.getIfSingle(e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM));

    presentation.setVisible(project != null);
    presentation.setEnabled(project != null && file != null && isEnabled(getRepositoryManager(project).getRepositoryForFile(file)));
  }

  private boolean isEnabled(@javax.annotation.Nullable T repository) {
    return repository != null && !repository.isFresh() && !noBranchesToCompare(repository);
  }

  @Nonnull
  protected abstract AbstractRepositoryManager<T> getRepositoryManager(@Nonnull Project project);

  protected abstract boolean noBranchesToCompare(@Nonnull T repository);

  @Nonnull
  protected abstract Collection<Change> getDiffChanges(@Nonnull Project project, @Nonnull VirtualFile file, @Nonnull String branchToCompare)
          throws VcsException;

  private class OnBranchChooseRunnable implements Runnable {
    private final Project myProject;
    private final VirtualFile myFile;
    private final String myHead;
    private final JList myList;

    private OnBranchChooseRunnable(@Nonnull Project project, @Nonnull VirtualFile file, @Nonnull String head, @Nonnull JList list) {
      myProject = project;
      myFile = file;
      myHead = head;
      myList = list;
    }

    @Override
    public void run() {
      Object selectedValue = myList.getSelectedValue();
      if (selectedValue == null) {
        LOG.error("Selected value is unexpectedly null");
        return;
      }
      showDiffWithBranchUnderModalProgress(myProject, myFile, myHead, selectedValue.toString());
    }
  }

  private void showDiffWithBranchUnderModalProgress(@Nonnull final Project project,
                                                    @Nonnull final VirtualFile file,
                                                    @Nonnull final String head,
                                                    @Nonnull final String compare) {
    new Task.Backgroundable(project, "Collecting Changes...", true) {
      private Collection<Change> changes;

      @Override
      public void run(@Nonnull ProgressIndicator indicator) {
        try {
          changes = getDiffChanges(project, file, compare);
        }
        catch (VcsException e) {
          VcsNotifier.getInstance(project).notifyImportantWarning("Couldn't compare with branch", String.format(
                  "Couldn't compare " + DvcsUtil.fileOrFolder(file) + " [%s] with branch [%s];\n %s", file, compare, e.getMessage()));
        }
      }

      @RequiredUIAccess
      @Override
      public void onSuccess() {
        //if changes null -> then exception occurred before
        if (changes != null) {
          VcsDiffUtil.showDiffFor(project, changes, VcsDiffUtil.getRevisionTitle(compare, false), VcsDiffUtil.getRevisionTitle(head, true),
                                  VcsUtil.getFilePath(file));
        }
      }
    }.queue();
  }

  protected static String fileDoesntExistInBranchError(@Nonnull VirtualFile file, @Nonnull String branchToCompare) {
    return String
            .format("%s <code>%s</code> doesn't exist in branch <code>%s</code>", StringUtil.capitalize(DvcsUtil.fileOrFolder(file)), file.getPresentableUrl(),
                    branchToCompare);
  }
}
