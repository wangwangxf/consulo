// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import consulo.application.AllIcons;
import com.intellij.idea.ActionsBundle;
import consulo.application.WriteAction;
import consulo.document.FileDocumentManager;
import consulo.fileEditor.event.FileEditorManagerEvent;
import consulo.fileEditor.event.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import consulo.project.Project;
import com.intellij.openapi.ui.Messages;
import consulo.virtualFileSystem.VirtualFile;
import consulo.project.ui.wm.StatusBar;
import consulo.project.ui.wm.StatusBarWidget;
import consulo.ui.ex.UIBundle;
import com.intellij.util.Consumer;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import consulo.fileEditor.impl.EditorsSplitters;
import consulo.ui.image.Image;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.awt.event.MouseEvent;
import java.io.IOException;

public final class ToggleReadOnlyAttributePanel implements StatusBarWidget.Multiframe, StatusBarWidget.IconPresentation {
  private StatusBar myStatusBar;

  @Override
  @Nullable
  public Image getIcon() {
    if (!isReadonlyApplicable()) {
      return null;
    }
    VirtualFile virtualFile = getCurrentFile();
    return virtualFile == null || virtualFile.isWritable() ? AllIcons.Ide.Readwrite : AllIcons.Ide.Readonly;
  }

  @Override
  @Nonnull
  public String ID() {
    return StatusBar.StandardWidgets.READONLY_ATTRIBUTE_PANEL;
  }

  @Override
  public StatusBarWidget copy() {
    return new ToggleReadOnlyAttributePanel();
  }

  @Override
  public WidgetPresentation getPresentation() {
    return this;
  }

  @Override
  public void dispose() {
    myStatusBar = null;
  }

  @Override
  public void install(@Nonnull StatusBar statusBar) {
    myStatusBar = statusBar;
    Project project = statusBar.getProject();
    if (project == null) {
      return;
    }

    project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@Nonnull FileEditorManagerEvent event) {
        if (myStatusBar != null) {
          myStatusBar.updateWidget(ID());
        }
      }
    });
  }

  @Override
  public String getTooltipText() {
    VirtualFile virtualFile = getCurrentFile();
    int writable = virtualFile == null || virtualFile.isWritable() ? 1 : 0;
    int readonly = writable == 1 ? 0 : 1;
    return ActionsBundle.message("action.ToggleReadOnlyAttribute.files", readonly, writable, 1, 0);
  }

  @Override
  public Consumer<MouseEvent> getClickConsumer() {
    return mouseEvent -> {
      final VirtualFile file = getCurrentFile();
      if (!isReadOnlyApplicableForFile(file)) {
        return;
      }
      FileDocumentManager.getInstance().saveAllDocuments();

      try {
        WriteAction.run(() -> ReadOnlyAttributeUtil.setReadOnlyAttribute(file, file.isWritable()));
        myStatusBar.updateWidget(ID());
      }
      catch (IOException e) {
        Messages.showMessageDialog(getProject(), e.getMessage(), UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
      }
    };
  }

  private boolean isReadonlyApplicable() {
    VirtualFile file = getCurrentFile();
    return isReadOnlyApplicableForFile(file);
  }

  private static boolean isReadOnlyApplicableForFile(@Nullable VirtualFile file) {
    return file != null && !file.getFileSystem().isReadOnly();
  }

  @Nullable
  private Project getProject() {
    return myStatusBar != null ? myStatusBar.getProject() : null;
  }

  @Nullable
  private VirtualFile getCurrentFile() {
    final Project project = getProject();
    if (project == null) return null;
    EditorsSplitters splitters = FileEditorManagerEx.getInstanceEx(project).getSplittersFor(myStatusBar.getComponent());
    return splitters.getCurrentFile();
  }
}
