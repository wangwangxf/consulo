/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package consulo.ide.impl.idea.ide.actions;

import consulo.application.dumb.DumbAware;
import consulo.fileEditor.FileEditorManager;
import consulo.fileEditor.FileEditorWindow;
import consulo.fileEditor.internal.FileEditorManagerEx;
import consulo.ide.IdeBundle;
import consulo.language.editor.CommonDataKeys;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.VirtualFile;

public class CloseAllEditorsAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(project, () -> {
      final FileEditorWindow window = e.getData(FileEditorWindow.DATA_KEY);
      if (window != null) {
        final VirtualFile[] files = window.getFiles();
        for (final VirtualFile file : files) {
          window.closeFile(file);
        }
        return;
      }
      FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
      VirtualFile selectedFile = fileEditorManager.getSelectedFiles()[0];
      VirtualFile[] openFiles = fileEditorManager.getSiblings(selectedFile);
      for (final VirtualFile openFile : openFiles) {
        fileEditorManager.closeFile(openFile);
      }
    }, IdeBundle.message("command.close.all.editors"), null);
  }

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    final FileEditorWindow editorWindow = event.getData(FileEditorWindow.DATA_KEY);
    if (editorWindow != null && editorWindow.inSplitter()) {
      presentation.setText(IdeBundle.message("action.close.all.editors.in.tab.group"));
    }
    else {
      presentation.setText(IdeBundle.message("action.close.all.editors"));
    }
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(FileEditorManager.getInstance(project).getSelectedFiles().length > 0);
  }
}
