// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.dvcs.ui;

import consulo.ide.impl.idea.dvcs.branch.DvcsBranchUtil;
import consulo.ide.impl.idea.dvcs.repo.Repository;
import consulo.ide.impl.idea.dvcs.repo.VcsRepositoryMappingListener;
import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.fileEditor.FileEditorManager;
import consulo.fileEditor.event.FileEditorManagerEvent;
import consulo.project.Project;
import consulo.ui.ex.popup.ListPopup;
import consulo.ide.impl.idea.openapi.util.text.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.project.ui.wm.StatusBar;
import consulo.project.ui.wm.StatusBarWidget;
import consulo.ide.impl.idea.openapi.wm.impl.status.EditorBasedWidget;
import consulo.ide.impl.idea.util.Consumer;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import org.jetbrains.annotations.Nls;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.awt.event.MouseEvent;

public abstract class DvcsStatusWidget<T extends Repository> extends EditorBasedWidget implements StatusBarWidget.MultipleTextValuesPresentation, StatusBarWidget.Multiframe {
  protected static final Logger LOG = Logger.getInstance(DvcsStatusWidget.class);

  @Nonnull
  private final String myVcsName;

  @Nullable
  private
  @Nls
  String myText;
  @Nullable
  private String myTooltip;
  @Nullable
  private Image myIcon;

  protected DvcsStatusWidget(@Nonnull Project project, @Nonnull @Nls String vcsName) {
    super(project);
    myVcsName = vcsName;

    project.getMessageBus().connect(this).subscribe(VcsRepositoryMappingListener.class, new VcsRepositoryMappingListener() {
      @Override
      public void mappingChanged() {
        LOG.debug("repository mappings changed");
        updateLater();
      }
    });
  }

  @Nullable
  protected abstract T guessCurrentRepository(@Nonnull Project project);

  @Nls
  @Nonnull
  protected abstract String getFullBranchName(@Nonnull T repository);

  @Nullable
  protected Image getIcon(@Nonnull T repository) {
    if (repository.getState() != Repository.State.NORMAL) return AllIcons.General.Warning;
    return PlatformIconGroup.vcsBranch();
  }

  protected abstract boolean isMultiRoot(@Nonnull Project project);

  @Nonnull
  protected abstract ListPopup getPopup(@Nonnull Project project, @Nonnull T repository);

  protected abstract void rememberRecentRoot(@Nonnull String path);

  @Override
  public void install(@Nonnull StatusBar statusBar) {
    super.install(statusBar);
    updateLater();
  }

  /**
   * @deprecated dvcs widgets are controlled by {@link consulo.ide.impl.idea.openapi.wm.impl.status.widget.StatusBarWidgetsManager}
   * and cannot be removed manually
   */
  @Deprecated
  public void deactivate() {
  }

  @Override
  public WidgetPresentation getPresentation() {
    return this;
  }

  @Override
  public void selectionChanged(@Nonnull FileEditorManagerEvent event) {
    LOG.debug("selection changed");
    update();
  }

  @Override
  public void fileOpened(@Nonnull FileEditorManager source, @Nonnull VirtualFile file) {
    LOG.debug("file opened");
    update();
  }

  @Override
  public void fileClosed(@Nonnull FileEditorManager source, @Nonnull VirtualFile file) {
    LOG.debug("file closed");
    update();
  }

  @Nullable
  @Override
  public String getSelectedValue() {
    return StringUtil.defaultIfEmpty(myText, "");
  }

  @Nullable
  @Override
  public String getTooltipText() {
    return myTooltip;
  }

  @Nullable
  @Override
  public Image getIcon() {
    return myIcon;
  }

  @Nullable
  @Override
  public ListPopup getPopupStep() {
    if (isDisposed()) return null;
    Project project = getProject();
    T repository = guessCurrentRepository(project);
    if (repository == null) return null;

    return getPopup(project, repository);
  }

  @Nullable
  @Override
  public Consumer<MouseEvent> getClickConsumer() {
    // has no effect since the click opens a list popup, and the consumer is not called for the MultipleTextValuesPresentation
    return null;
  }

  protected void updateLater() {
    Project project = getProject();
    if (isDisposed()) return;
    ApplicationManager.getApplication().invokeLater(() -> {
      LOG.debug("update after repository change");
      update();
    }, project.getDisposed());
  }

  private void update() {
    myText = null;
    myTooltip = null;
    myIcon = null;

    if (isDisposed()) return;
    Project project = getProject();
    T repository = guessCurrentRepository(project);
    if (repository == null) return;
    myText = DvcsBranchUtil.shortenBranchName(getFullBranchName(repository));
    myTooltip = getToolTip(repository);
    myIcon = getIcon(repository);
    if (myStatusBar != null) {
      myStatusBar.updateWidget(ID());
    }
    rememberRecentRoot(repository.getRoot().getPath());
  }

  @Nullable
  private String getToolTip(@Nullable T repository) {
    if (repository == null) return null;
    String message = DvcsBundle.message("tooltip.branch.widget.vcs.branch.name.text", myVcsName, getFullBranchName(repository));
    if (isMultiRoot(repository.getProject())) {
      message += "\n";
      message += DvcsBundle.message("tooltip.branch.widget.root.name.text", repository.getRoot().getName());
    }
    return message;
  }
}