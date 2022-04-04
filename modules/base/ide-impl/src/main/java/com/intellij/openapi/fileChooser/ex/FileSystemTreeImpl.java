// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.ex;

import consulo.ui.ex.UIBundle;
import consulo.ui.ex.awt.PopupHandler;
import consulo.ui.ex.awt.event.DoubleClickListener;
import consulo.ui.ex.awt.speedSearch.TreeSpeedSearch;
import consulo.ui.ex.awt.tree.AbstractTreeBuilder;
import consulo.ui.ex.tree.AbstractTreeStructure;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.ui.ex.awt.tree.NodeRenderer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionManager;
import consulo.application.ApplicationManager;
import consulo.application.impl.internal.IdeaModalityState;
import consulo.undoRedo.CommandProcessor;
import consulo.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.impl.FileComparator;
import com.intellij.openapi.fileChooser.impl.FileTreeBuilder;
import com.intellij.openapi.fileChooser.impl.FileTreeStructure;
import com.intellij.openapi.fileChooser.tree.*;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.project.Project;
import consulo.application.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import consulo.ui.ex.awt.tree.AsyncTreeModel;
import consulo.ui.ex.awt.tree.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

public class FileSystemTreeImpl implements FileSystemTree {
  private final Tree myTree;
  private final FileTreeStructure myTreeStructure;
  private final AbstractTreeBuilder myTreeBuilder;
  private final Project myProject;
  private final ArrayList<Runnable> myOkActions = new ArrayList<>(2);
  private final FileChooserDescriptor myDescriptor;
  private final FileTreeModel myFileTreeModel;
  private final AsyncTreeModel myAsyncTreeModel;

  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final MyExpansionListener myExpansionListener = new MyExpansionListener();

  private final Set<VirtualFile> myEverExpanded = new HashSet<>();

  public FileSystemTreeImpl(@Nullable final Project project, final FileChooserDescriptor descriptor) {
    this(project, descriptor, new Tree(), null, null, null);
    myTree.setRootVisible(descriptor.isTreeRootVisible());
    myTree.setShowsRootHandles(true);
  }

  public FileSystemTreeImpl(@Nullable final Project project,
                            final FileChooserDescriptor descriptor,
                            final Tree tree,
                            @Nullable TreeCellRenderer renderer,
                            @Nullable final Runnable onInitialized,
                            @Nullable final Convertor<? super TreePath, String> speedSearchConverter) {
    myProject = project;
    //noinspection deprecation
    if (renderer == null && useNewAsyncModel()) {
      renderer = new FileRenderer().forTree();
      myFileTreeModel = new FileTreeModel(descriptor, new FileRefresher(true, 3, () -> IdeaModalityState.stateForComponent(tree)));
      myAsyncTreeModel = new AsyncTreeModel(myFileTreeModel, false, this);
      myTreeStructure = null;
    }
    else {
      myFileTreeModel = null;
      myAsyncTreeModel = null;
      myTreeStructure = new FileTreeStructure(project, descriptor);
    }
    myDescriptor = descriptor;
    myTree = tree;
    if (myAsyncTreeModel != null) {
      myTree.setModel(myAsyncTreeModel);
      myTreeBuilder = null;
    }
    else {
      final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
      myTree.setModel(treeModel);

      myTree.addTreeExpansionListener(myExpansionListener);

      myTreeBuilder = createTreeBuilder(myTree, treeModel, myTreeStructure, FileComparator.getInstance(), descriptor, () -> {
        myTree.expandPath(new TreePath(treeModel.getRoot()));
        if (onInitialized != null) {
          onInitialized.run();
        }
      });

      Disposer.register(myTreeBuilder, new Disposable() {
        @Override
        public void dispose() {
          myTree.removeTreeExpansionListener(myExpansionListener);
        }
      });

      if (project != null) {
        Disposer.register(project, myTreeBuilder);
      }
    }

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(final TreeSelectionEvent e) {
        processSelectionChange();
      }
    });

    if (speedSearchConverter != null) {
      new TreeSpeedSearch(myTree, speedSearchConverter);
    }
    else {
      new TreeSpeedSearch(myTree);
    }
    TreeUtil.installActions(myTree);

    myTree.getSelectionModel().setSelectionMode(descriptor.isChooseMultiple() ? TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION : TreeSelectionModel.SINGLE_TREE_SELECTION);
    registerTreeActions();

    if (renderer == null) {
      renderer = new NodeRenderer() {
        @RequiredUIAccess
        @Override
        public void customizeCellRenderer(@Nonnull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
          super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
          final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          if (userObject instanceof FileNodeDescriptor) {
            String comment = ((FileNodeDescriptor)userObject).getComment();
            if (comment != null) {
              append(comment, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
        }
      };
    }
    myTree.setCellRenderer(renderer);
  }

  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  protected boolean useNewAsyncModel() {
    return Registry.is("file.chooser.async.tree.model");
  }

  protected AbstractTreeBuilder createTreeBuilder(final JTree tree,
                                                  DefaultTreeModel treeModel,
                                                  final AbstractTreeStructure treeStructure,
                                                  final Comparator<NodeDescriptor> comparator,
                                                  FileChooserDescriptor descriptor,
                                                  @Nullable final Runnable onInitialized) {
    return new FileTreeBuilder(tree, treeModel, treeStructure, comparator, descriptor, onInitialized);
  }

  private void registerTreeActions() {
    myTree.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        performEnterAction(true);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        performEnterAction(false);
        return true;
      }
    }.installOn(myTree);
  }

  private void performEnterAction(boolean toggleNodeState) {
    TreePath path = myTree.getSelectionPath();
    if (path != null) {
      if (isLeaf(path)) {
        fireOkAction();
      }
      else if (toggleNodeState) {
        if (myTree.isExpanded(path)) {
          myTree.collapsePath(path);
        }
        else {
          myTree.expandPath(path);
        }
      }
    }
  }

  public void addOkAction(Runnable action) {
    myOkActions.add(action);
  }

  private void fireOkAction() {
    for (Runnable action : myOkActions) {
      action.run();
    }
  }

  public void registerMouseListener(final ActionGroup group) {
    PopupHandler.installUnknownPopupHandler(myTree, group, ActionManager.getInstance());
  }

  @Override
  public boolean areHiddensShown() {
    if (myAsyncTreeModel != null) {
      return myDescriptor.isShowHiddenFiles();
    }
    else {
      return myTreeStructure.areHiddensShown();
    }
  }

  @Override
  public void showHiddens(boolean showHidden) {
    if (myAsyncTreeModel != null) {
      myDescriptor.withShowHiddenFiles(showHidden);
      if (myFileTreeModel != null) myFileTreeModel.invalidate();
    }
    else {
      myTreeStructure.showHiddens(showHidden);
    }
    updateTree();
  }

  @Override
  public void updateTree() {
    if (myTreeBuilder != null) {
      myTreeBuilder.queueUpdate();
    }
  }

  @Override
  public void dispose() {
    if (myTreeBuilder != null) {
      Disposer.dispose(myTreeBuilder);
    }
    myEverExpanded.clear();
  }

  public AbstractTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  @Override
  public void select(VirtualFile file, @Nullable final Runnable onDone) {
    select(new VirtualFile[]{file}, onDone);
  }

  @Override
  public void select(VirtualFile[] file, @Nullable final Runnable onDone) {
    if (myAsyncTreeModel != null) {
      switch (file.length) {
        case 0:
          myTree.clearSelection();
          if (onDone != null) onDone.run();
          break;
        case 1:
          myTree.clearSelection();
          TreeUtil.promiseSelect(myTree, new FileNodeVisitor(file[0])).onProcessed(path -> {
            if (onDone != null) onDone.run();
          });
          break;
        default:
          myTree.clearSelection();
          TreeUtil.promiseSelect(myTree, Stream.of(file).map(FileNodeVisitor::new)).onProcessed(paths -> {
            if (onDone != null) onDone.run();
          });
          break;
      }
    }
    else {
      Object[] elements = new Object[file.length];
      for (int i = 0; i < file.length; i++) {
        VirtualFile eachFile = file[i];
        elements[i] = getFileElementFor(eachFile);
      }

      myTreeBuilder.select(elements, onDone);
    }
  }

  @Override
  public void expand(final VirtualFile file, @Nullable final Runnable onDone) {
    if (myAsyncTreeModel != null) {
      TreeUtil.promiseExpand(myTree, new FileNodeVisitor(file)).onSuccess(path -> {
        if (path != null && onDone != null) onDone.run();
      });
    }
    else {
      myTreeBuilder.expand(getFileElementFor(file), onDone);
    }
  }

  @Nullable
  private static FileElement getFileElementFor(@Nonnull VirtualFile file) {
    VirtualFile selectFile;

    if ((file.getFileSystem() instanceof ArchiveFileSystem) && file.getParent() == null) {
      selectFile = ArchiveVfsUtil.getVirtualFileForJar(file);
      if (selectFile == null) {
        return null;
      }
    }
    else {
      selectFile = file;
    }

    return new FileElement(selectFile, selectFile.getName());
  }

  public Exception createNewFolder(final VirtualFile parentDirectory, final String newFolderName) {
    final Exception[] failReason = new Exception[]{null};
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              VirtualFile parent = parentDirectory;
              for (String name : StringUtil.tokenize(newFolderName, "\\/")) {
                VirtualFile folder = parent.createChildDirectory(this, name);
                updateTree();
                select(folder, null);
                parent = folder;
              }
            }
            catch (IOException e) {
              failReason[0] = e;
            }
          }
        });
      }
    }, UIBundle.message("file.chooser.create.new.folder.command.name"), null);
    return failReason[0];
  }

  public Exception createNewFile(final VirtualFile parentDirectory, final String newFileName, final FileType fileType, final String initialContent) {
    final Exception[] failReason = new Exception[]{null};
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              final String newFileNameWithExtension = newFileName.endsWith('.' + fileType.getDefaultExtension()) ? newFileName : newFileName + '.' + fileType.getDefaultExtension();
              final VirtualFile file = parentDirectory.createChildData(this, newFileNameWithExtension);
              VfsUtil.saveText(file, initialContent != null ? initialContent : "");
              updateTree();
              select(file, null);
            }
            catch (IOException e) {
              failReason[0] = e;
            }
          }
        });
      }
    }, UIBundle.message("file.chooser.create.new.file.command.name"), null);
    return failReason[0];
  }

  @Override
  public JTree getTree() {
    return myTree;
  }

  @Override
  @Nullable
  public VirtualFile getSelectedFile() {
    final TreePath path = myTree.getSelectionPath();
    if (path == null) return null;
    return getVirtualFile(path);
  }

  @Override
  @Nullable
  public VirtualFile getNewFileParent() {
    final VirtualFile selected = getSelectedFile();
    if (selected != null) return selected;

    final List<VirtualFile> roots = myDescriptor.getRoots();
    return roots.size() == 1 ? roots.get(0) : null;
  }

  @Override
  public <T> T getData(@Nonnull Key<T> key) {
    return myDescriptor.getUserData(key);
  }

  @Override
  @Nonnull
  public VirtualFile[] getSelectedFiles() {
    final TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) return VirtualFile.EMPTY_ARRAY;

    final List<VirtualFile> files = new ArrayList<>();
    for (TreePath path : paths) {
      VirtualFile file = getVirtualFile(path);
      if (file != null && file.isValid()) {
        files.add(file);
      }
    }
    return VfsUtilCore.toVirtualFileArray(files);
  }

  private boolean isLeaf(TreePath path) {
    Object component = path.getLastPathComponent();
    if (component instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)component;
      return node.isLeaf();
    }
    return myAsyncTreeModel != null && myAsyncTreeModel.isLeaf(component);
  }

  public static VirtualFile getVirtualFile(TreePath path) {
    Object component = path.getLastPathComponent();
    if (component instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)component;
      Object userObject = node.getUserObject();
      if (userObject instanceof FileNodeDescriptor) {
        FileNodeDescriptor descriptor = (FileNodeDescriptor)userObject;
        return descriptor.getElement().getFile();
      }
    }
    if (component instanceof FileNode) {
      FileNode node = (FileNode)component;
      return node.getFile();
    }
    return null;
  }

  @Override
  public boolean selectionExists() {
    TreePath[] selectedPaths = myTree.getSelectionPaths();
    return selectedPaths != null && selectedPaths.length != 0;
  }

  @Override
  public boolean isUnderRoots(@Nonnull VirtualFile file) {
    final List<VirtualFile> roots = myDescriptor.getRoots();
    if (roots.size() == 0) return true;

    for (VirtualFile root : roots) {
      if (root != null && VfsUtilCore.isAncestor(root, file, false)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void addListener(final Listener listener, final Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  private void fireSelection(@Nonnull List<? extends VirtualFile> selection) {
    for (Listener each : myListeners) {
      each.selectionChanged(selection);
    }
  }

  private void processSelectionChange() {
    if (myListeners.size() == 0) return;
    List<VirtualFile> selection = new ArrayList<>();

    final TreePath[] paths = myTree.getSelectionPaths();
    if (paths != null) {
      for (TreePath each : paths) {
        VirtualFile file = getVirtualFile(each);
        if (file != null) {
          selection.add(file);
        }
      }
    }

    fireSelection(selection);
  }

  private class MyExpansionListener implements TreeExpansionListener {
    @Override
    public void treeExpanded(final TreeExpansionEvent event) {
      if (myTreeBuilder == null || !myTreeBuilder.isNodeBeingBuilt(event.getPath())) return;

      TreePath path = event.getPath();
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node.getUserObject() instanceof FileNodeDescriptor) {
        FileNodeDescriptor nodeDescriptor = (FileNodeDescriptor)node.getUserObject();
        final FileElement fileDescriptor = nodeDescriptor.getElement();
        final VirtualFile virtualFile = fileDescriptor.getFile();
        if (virtualFile != null) {
          if (!myEverExpanded.contains(virtualFile)) {
            if (virtualFile instanceof NewVirtualFile) {
              ((NewVirtualFile)virtualFile).markDirty();
            }
            myEverExpanded.add(virtualFile);
          }


          final boolean async = myTreeBuilder.isToBuildChildrenInBackground(virtualFile);
          if (virtualFile instanceof NewVirtualFile) {
            RefreshQueue.getInstance().refresh(async, false, null, IdeaModalityState.stateForComponent(myTree), virtualFile);
          }
          else {
            virtualFile.refresh(async, false);
          }
        }
      }
    }

    @Override
    public void treeCollapsed(TreeExpansionEvent event) {
    }
  }
}
