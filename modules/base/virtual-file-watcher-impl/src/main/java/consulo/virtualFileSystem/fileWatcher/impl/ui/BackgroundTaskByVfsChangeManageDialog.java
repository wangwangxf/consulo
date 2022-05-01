/*
 * Copyright 2013-2016 consulo.io
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
package consulo.virtualFileSystem.fileWatcher.impl.ui;

import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.awt.CheckBoxList;
import consulo.ide.impl.idea.ui.EditorNotifications;
import consulo.ui.ex.awt.ScrollingUtil;
import consulo.ui.ex.awt.ToolbarDecorator;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.VerticalFlowLayout;
import consulo.project.Project;
import consulo.ui.ex.popup.ListPopup;
import consulo.ui.ex.popup.ListPopupStep;
import consulo.ui.ex.popup.PopupStep;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileWatcher.BackgroundTaskByVfsChangeManager;
import consulo.virtualFileSystem.fileWatcher.BackgroundTaskByVfsChangeProvider;
import consulo.virtualFileSystem.fileWatcher.BackgroundTaskByVfsChangeTask;
import consulo.virtualFileSystem.fileWatcher.impl.BackgroundTaskByVfsChangeProviders;
import consulo.virtualFileSystem.fileWatcher.impl.BackgroundTaskByVfsParametersImpl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 30.04.14
 */
public class BackgroundTaskByVfsChangeManageDialog extends DialogWrapper {
  private final CheckBoxList<BackgroundTaskByVfsChangeTask> myBoxlist;
  private final Project myProject;
  private final VirtualFile myVirtualFile;
  private JPanel myPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true));
  private BackgroundTaskByVfsChangePanel myVfsChangePanel;

  private BackgroundTaskByVfsChangeTask myPrevTask;

  public BackgroundTaskByVfsChangeManageDialog(@Nonnull final Project project, final VirtualFile virtualFile) {
    super(project);
    myProject = project;
    myVirtualFile = virtualFile;

    myVfsChangePanel = new BackgroundTaskByVfsChangePanel(project);
    myVfsChangePanel.reset(BackgroundTaskByVfsParametersImpl.EMPTY);

    myBoxlist = new CheckBoxList<>();
    myBoxlist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myBoxlist.setCheckBoxListListener((index, value) -> {
      BackgroundTaskByVfsChangeTask task = myBoxlist.getItemAt(index);
      if (task == null) {
        return;
      }
      task.setEnabled(value);
    });
    myBoxlist.addListSelectionListener(e -> {
      if (myPrevTask != null) {
        myVfsChangePanel.applyTo(myPrevTask.getParameters());
        myPrevTask = null;
      }

      if (myBoxlist.getItemsCount() == 0 || myBoxlist.getSelectedIndex() == -1) {
        myVfsChangePanel.reset(BackgroundTaskByVfsParametersImpl.EMPTY);
        return;
      }
      BackgroundTaskByVfsChangeTask task = myBoxlist.getItemAt(myBoxlist.getSelectedIndex());
      if (task == null) {
        myVfsChangePanel.reset(BackgroundTaskByVfsParametersImpl.EMPTY);
        return;
      }
      myVfsChangePanel.reset(task.getParameters());
      myPrevTask = task;
    });
    myBoxlist.setBorder(null);
    myBoxlist.setPreferredSize(JBUI.size(550, 200));

    final List<BackgroundTaskByVfsChangeProvider> providers = BackgroundTaskByVfsChangeProviders.getProviders(project, virtualFile);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myBoxlist);
    decorator = decorator.setAddActionUpdater(e -> !providers.isEmpty());
    decorator = decorator.setAddAction(anActionButton -> {

      if (providers.size() > 1) {
        ListPopupStep<BackgroundTaskByVfsChangeProvider> listPopupStep = new BaseListPopupStep<BackgroundTaskByVfsChangeProvider>("Add", providers) {
          @Nonnull
          @Override
          public String getTextFor(BackgroundTaskByVfsChangeProvider value) {
            return value.getTemplateName();
          }

          @Override
          public PopupStep onChosen(final BackgroundTaskByVfsChangeProvider val, boolean finalChoice) {
            return doFinalStep(() -> add(val));
          }
        };
        ListPopup listPopup = JBPopupFactory.getInstance().createListPopup(listPopupStep);
        listPopup.show(anActionButton.getPreferredPopupPoint());
      }
      else {
        add(providers.get(0));
      }
    });

    List<BackgroundTaskByVfsChangeTask> originalTasks = BackgroundTaskByVfsChangeManager.getInstance(project).findTasks(virtualFile);

    List<BackgroundTaskByVfsChangeTask> cloneTasks = new ArrayList<>(originalTasks.size());
    for (BackgroundTaskByVfsChangeTask task : originalTasks) {
      cloneTasks.add(task.clone());
    }

    myBoxlist.setItems(cloneTasks, BackgroundTaskByVfsChangeTask::getName, BackgroundTaskByVfsChangeTask::isEnabled);

    ScrollingUtil.ensureSelectionExists(myBoxlist);

    myPanel.add(decorator.createPanel());
    myPanel.add(myVfsChangePanel);
    setTitle("Manage Background Tasks");
    init();
  }

  private void add(@Nonnull BackgroundTaskByVfsChangeProvider provider) {
    String name = Messages.showInputDialog(myProject, "Name", "Enter Name", UIUtil.getInformationIcon(), provider.getTemplateName(), null);
    if (name == null) {
      return;
    }

    BackgroundTaskByVfsChangeTask task = BackgroundTaskByVfsChangeManager.getInstance(myProject).createTask(provider, myVirtualFile, name);

    myBoxlist.addItem(task, task.getName(), task.isEnabled());
    // select last item - selected item
    myBoxlist.setSelectedIndex(myBoxlist.getItemsCount() - 1);
  }

  @Nonnull
  private List<BackgroundTaskByVfsChangeTask> getTasks() {
    List<BackgroundTaskByVfsChangeTask> list = new ArrayList<>();
    for (int i = 0; i < myBoxlist.getItemsCount(); i++) {
      BackgroundTaskByVfsChangeTask task = myBoxlist.getItemAt(i);
      list.add(task);
    }
    return list;
  }

  @Override
  protected void doOKAction() {
    if (myPrevTask != null) {
      myVfsChangePanel.applyTo(myPrevTask.getParameters());
    }

    BackgroundTaskByVfsChangeManager vfsChangeManager = BackgroundTaskByVfsChangeManager.getInstance(myProject);

    List<BackgroundTaskByVfsChangeTask> originalTasks = vfsChangeManager.findTasks(myVirtualFile);
    for (BackgroundTaskByVfsChangeTask originalTask : originalTasks) {
      vfsChangeManager.removeTask(originalTask);
    }

    List<BackgroundTaskByVfsChangeTask> tasks = getTasks();
    for (BackgroundTaskByVfsChangeTask task : tasks) {
      vfsChangeManager.registerTask(task);
    }
    EditorNotifications.updateAll();
    super.doOKAction();
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "#BackgroundTaskByVfsChangeManageDialog";
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
