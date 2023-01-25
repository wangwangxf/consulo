// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.openapi.fileEditor.impl;

import consulo.annotation.component.ServiceImpl;
import consulo.application.ApplicationManager;
import consulo.application.ui.UISettings;
import consulo.application.util.DateFormatUtil;
import consulo.application.util.registry.Registry;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorFactory;
import consulo.codeEditor.event.CaretEvent;
import consulo.codeEditor.event.EditorEventMulticaster;
import consulo.component.messagebus.MessageBus;
import consulo.component.messagebus.MessageBusConnection;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.RangeMarker;
import consulo.document.event.DocumentEvent;
import consulo.fileEditor.*;
import consulo.fileEditor.event.FileEditorManagerEvent;
import consulo.fileEditor.event.FileEditorManagerListener;
import consulo.fileEditor.internal.FileEditorManagerEx;
import consulo.fileEditor.text.TextEditorProvider;
import consulo.ide.impl.idea.openapi.command.impl.CommandMerger;
import consulo.ide.impl.idea.openapi.command.impl.FocusBasedCurrentEditorProvider;
import consulo.ide.impl.idea.openapi.editor.event.EditorEventListener;
import consulo.ide.impl.idea.openapi.fileEditor.ex.IdeDocumentHistory;
import consulo.ide.impl.idea.openapi.project.ProjectUtil;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.index.io.EnumeratorLongDescriptor;
import consulo.index.io.EnumeratorStringDescriptor;
import consulo.index.io.PersistentHashMap;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.psi.internal.ExternalChangeAction;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.SimpleColoredComponent;
import consulo.undoRedo.CommandProcessor;
import consulo.undoRedo.event.CommandEvent;
import consulo.undoRedo.event.CommandListener;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.event.BulkFileListener;
import consulo.virtualFileSystem.event.VFileDeleteEvent;
import consulo.virtualFileSystem.event.VFileEvent;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;

@State(name = "IdeDocumentHistory", storages = @Storage(value = StoragePathMacros.WORKSPACE_FILE))
@ServiceImpl
public class IdeDocumentHistoryImpl extends IdeDocumentHistory implements Disposable, PersistentStateComponent<IdeDocumentHistoryImpl.RecentlyChangedFilesState> {
  public interface SkipFromDocumentHistory {
  }

  private static final Logger LOG = Logger.getInstance(IdeDocumentHistoryImpl.class);

  private static final int BACK_QUEUE_LIMIT = Registry.intValue("editor.navigation.history.stack.size");
  private static final int CHANGE_QUEUE_LIMIT = Registry.intValue("editor.navigation.history.stack.size");

  private final Project myProject;

  private FileDocumentManager myFileDocumentManager;

  private final LinkedList<PlaceInfo> myBackPlaces = new LinkedList<>(); // LinkedList of PlaceInfo's
  private final LinkedList<PlaceInfo> myForwardPlaces = new LinkedList<>(); // LinkedList of PlaceInfo's
  private boolean myBackInProgress;
  private boolean myForwardInProgress;
  private Object myLastGroupId;
  private boolean myRegisteredBackPlaceInLastGroup;

  // change's navigation
  private final LinkedList<PlaceInfo> myChangePlaces = new LinkedList<>(); // LinkedList of PlaceInfo's
  private int myCurrentIndex;

  private PlaceInfo myCommandStartPlace;
  private boolean myCurrentCommandIsNavigation;
  private boolean myCurrentCommandHasChanges;
  private final Set<VirtualFile> myChangedFilesInCurrentCommand = new HashSet<>();
  private boolean myCurrentCommandHasMoves;

  private final PersistentHashMap<String, Long> myRecentFilesTimestampsMap;

  private RecentlyChangedFilesState myRecentlyChangedFiles = new RecentlyChangedFilesState();

  @Inject
  public IdeDocumentHistoryImpl(@Nonnull Project project) {
    myProject = project;

    MessageBusConnection busConnection = project.getMessageBus().connect(this);
    busConnection.subscribe(FileEditorManagerListener.class, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@Nonnull FileEditorManagerEvent e) {
        onSelectionChanged();
      }
    });
    busConnection.subscribe(BulkFileListener.class, new BulkFileListener() {
      @Override
      public void after(@Nonnull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFileDeleteEvent) {
            removeInvalidFilesFromStacks();
            return;
          }
        }
      }
    });
    busConnection.subscribe(CommandListener.class, new CommandListener() {
      @Override
      public void commandStarted(@Nonnull CommandEvent event) {
        onCommandStarted();
      }

      @Override
      public void commandFinished(@Nonnull CommandEvent event) {
        onCommandFinished(event.getProject(), event.getCommandGroupId());
      }
    });

    EditorEventListener listener = new EditorEventListener() {
      @Override
      public void documentChanged(@Nonnull DocumentEvent e) {
        Document document = e.getDocument();
        final VirtualFile file = getFileDocumentManager().getFile(document);
        if (file != null && !(file instanceof LightVirtualFile) && !ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction.class)) {
          if (!ApplicationManager.getApplication().isDispatchThread()) {
            LOG.error("Document update for physical file not in EDT: " + file);
          }
          myCurrentCommandHasChanges = true;
          myChangedFilesInCurrentCommand.add(file);
        }
      }

      @Override
      public void caretPositionChanged(@Nonnull CaretEvent e) {
        if (e.getOldPosition().line == e.getNewPosition().line) {
          return;
        }

        Document document = e.getEditor().getDocument();
        if (getFileDocumentManager().getFile(document) != null) {
          myCurrentCommandHasMoves = true;
        }
      }
    };
    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
    multicaster.addDocumentListener(listener, this);
    multicaster.addCaretListener(listener, this);

    myRecentFilesTimestampsMap = initRecentFilesTimestampMap(project);
  }

  protected FileEditorManagerEx getFileEditorManager() {
    return FileEditorManagerEx.getInstanceEx(myProject);
  }

  @Nonnull
  private PersistentHashMap<String, Long> initRecentFilesTimestampMap(@Nonnull Project project) {
    File file = ProjectUtil.getProjectCachePath(project, "recentFilesTimeStamps.dat").toFile();
    PersistentHashMap<String, Long> map;
    try {
      map = new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE, EnumeratorLongDescriptor.INSTANCE);
    }
    catch (IOException e) {
      LOG.info("Cannot create PersistentHashMap in " + file, e);
      PersistentHashMap.deleteFilesStartingWith(file);
      try {
        map = new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE, EnumeratorLongDescriptor.INSTANCE);
      }
      catch (IOException e1) {
        LOG.error("Cannot create PersistentHashMap in " + file + " even after deleting old files", e1);
        throw new RuntimeException(e);
      }
    }
    PersistentHashMap<String, Long> finalMap = map;
    Disposer.register(this, () -> {
      try {
        finalMap.close();
      }
      catch (IOException e) {
        LOG.info("Cannot close persistent viewed files timestamps hash map", e);
      }
    });
    return map;
  }

  private void registerViewed(@Nonnull VirtualFile file) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    try {
      myRecentFilesTimestampsMap.put(file.getPath(), System.currentTimeMillis());
    }
    catch (IOException e) {
      LOG.info("Cannot put a timestamp from a persistent hash map", e);
    }
  }

  public static void appendTimestamp(@Nonnull Project project, @Nonnull SimpleColoredComponent component, @Nonnull VirtualFile file) {
    if (!UISettings.getInstance().getShowInplaceComments()) {
      return;
    }

    try {
      Long timestamp = getInstance(project).getRecentFilesTimestamps().get(file.getPath());
      if (timestamp != null) {
        component.append(" ");
        component.append(DateFormatUtil.formatPrettyDateTime(timestamp), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
      }
    }
    catch (IOException e) {
      LOG.info("Cannot get a timestamp from a persistent hash map", e);
    }
  }

  public static class RecentlyChangedFilesState {
    // don't make it private, see: IDEA-130363 Recently Edited Files list should survive restart
    @SuppressWarnings("WeakerAccess")
    public List<String> CHANGED_PATHS = new ArrayList<>();

    public void register(VirtualFile file) {
      final String path = file.getPath();
      CHANGED_PATHS.remove(path);
      CHANGED_PATHS.add(path);
      trimToSize();
    }

    private void trimToSize() {
      final int limit = UISettings.getInstance().getRecentFilesLimit() + 1;
      while (CHANGED_PATHS.size() > limit) {
        CHANGED_PATHS.remove(0);
      }
    }
  }

  @Override
  public RecentlyChangedFilesState getState() {
    return myRecentlyChangedFiles;
  }

  @Override
  public void loadState(@Nonnull RecentlyChangedFilesState state) {
    myRecentlyChangedFiles = state;
  }

  public final void onSelectionChanged() {
    myCurrentCommandIsNavigation = true;
    myCurrentCommandHasMoves = true;
  }

  final void onCommandStarted() {
    myCommandStartPlace = getCurrentPlaceInfo();
    myCurrentCommandIsNavigation = false;
    myCurrentCommandHasChanges = false;
    myCurrentCommandHasMoves = false;
    myChangedFilesInCurrentCommand.clear();
  }

  @Nullable
  private PlaceInfo getCurrentPlaceInfo() {
    FileEditorWithProvider selectedEditorWithProvider = getSelectedEditor();
    if (selectedEditorWithProvider == null) {
      return null;
    }
    return createPlaceInfo(selectedEditorWithProvider.getFileEditor(), selectedEditorWithProvider.getProvider());
  }

  @Nullable
  private static PlaceInfo getPlaceInfoFromFocus() {
    FileEditor fileEditor = new FocusBasedCurrentEditorProvider().getCurrentEditor();
    if (fileEditor instanceof TextEditor && fileEditor.isValid()) {
      VirtualFile file = fileEditor.getFile();
      if (file != null) {
        return new PlaceInfo(file, fileEditor.getState(FileEditorStateLevel.NAVIGATION), TextEditorProvider.getInstance().getEditorTypeId(), null, getCaretPosition(fileEditor),
                             System.currentTimeMillis());
      }
    }
    return null;
  }

  final void onCommandFinished(Project project, Object commandGroupId) {
    if (!CommandMerger.canMergeGroup(commandGroupId, myLastGroupId)) myRegisteredBackPlaceInLastGroup = false;
    myLastGroupId = commandGroupId;

    if (myCommandStartPlace != null && myCurrentCommandIsNavigation && myCurrentCommandHasMoves) {
      if (!myBackInProgress) {
        if (!myRegisteredBackPlaceInLastGroup) {
          myRegisteredBackPlaceInLastGroup = true;
          putLastOrMerge(myCommandStartPlace, BACK_QUEUE_LIMIT, false);
          registerViewed(myCommandStartPlace.myFile);
        }
        if (!myForwardInProgress) {
          myForwardPlaces.clear();
        }
      }
      removeInvalidFilesFromStacks();
    }

    if (myCurrentCommandHasChanges) {
      setCurrentChangePlace(project == myProject);
    }
    else if (myCurrentCommandHasMoves) {
      myCurrentIndex = myChangePlaces.size();
    }
  }

  @Override
  public final void includeCurrentCommandAsNavigation() {
    myCurrentCommandIsNavigation = true;
  }

  @Override
  public void setCurrentCommandHasMoves() {
    myCurrentCommandHasMoves = true;
  }

  @Override
  public final void includeCurrentPlaceAsChangePlace() {
    setCurrentChangePlace(false);
  }

  private void setCurrentChangePlace(boolean acceptPlaceFromFocus) {
    PlaceInfo placeInfo = getCurrentPlaceInfo();
    if (placeInfo != null && !myChangedFilesInCurrentCommand.contains(placeInfo.getFile())) {
      placeInfo = null;
    }
    if (placeInfo == null && acceptPlaceFromFocus) {
      placeInfo = getPlaceInfoFromFocus();
    }
    if (placeInfo != null && !myChangedFilesInCurrentCommand.contains(placeInfo.getFile())) {
      placeInfo = null;
    }
    if (placeInfo == null) {
      return;
    }

    myRecentlyChangedFiles.register(placeInfo.getFile());

    putLastOrMerge(placeInfo, CHANGE_QUEUE_LIMIT, true);
    myCurrentIndex = myChangePlaces.size();
  }

  @Override
  public VirtualFile[] getChangedFiles() {
    List<VirtualFile> files = new ArrayList<>();

    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final List<String> paths = myRecentlyChangedFiles.CHANGED_PATHS;
    for (String path : paths) {
      final VirtualFile file = lfs.findFileByPath(path);
      if (file != null) {
        files.add(file);
      }
    }

    return VfsUtilCore.toVirtualFileArray(files);
  }

  @Override
  public PersistentHashMap<String, Long> getRecentFilesTimestamps() {
    return myRecentFilesTimestampsMap;
  }

  boolean isRecentlyChanged(@Nonnull VirtualFile file) {
    return myRecentlyChangedFiles.CHANGED_PATHS.contains(file.getPath());
  }

  @Override
  public final void clearHistory() {
    myBackPlaces.clear();
    myForwardPlaces.clear();
    myChangePlaces.clear();

    myLastGroupId = null;

    myCurrentIndex = 0;
    myCommandStartPlace = null;
  }

  @Override
  public final void back() {
    removeInvalidFilesFromStacks();
    if (myBackPlaces.isEmpty()) return;
    final PlaceInfo info = myBackPlaces.removeLast();
    myProject.getMessageBus().syncPublisher(RecentPlacesListener.class).recentPlaceRemoved(info, false);

    PlaceInfo current = getCurrentPlaceInfo();
    if (current != null) myForwardPlaces.add(current);

    myBackInProgress = true;
    try {
      executeCommand(() -> gotoPlaceInfo(info), "", null);
    }
    finally {
      myBackInProgress = false;
    }
  }

  @Override
  public final void forward() {
    removeInvalidFilesFromStacks();

    final PlaceInfo target = getTargetForwardInfo();
    if (target == null) return;

    myForwardInProgress = true;
    try {
      executeCommand(() -> gotoPlaceInfo(target), "", null);
    }
    finally {
      myForwardInProgress = false;
    }
  }

  private PlaceInfo getTargetForwardInfo() {
    if (myForwardPlaces.isEmpty()) return null;

    PlaceInfo target = myForwardPlaces.removeLast();
    PlaceInfo current = getCurrentPlaceInfo();

    while (!myForwardPlaces.isEmpty()) {
      if (current != null && isSame(current, target)) {
        target = myForwardPlaces.removeLast();
      }
      else {
        break;
      }
    }
    return target;
  }

  @Override
  public final boolean isBackAvailable() {
    return !myBackPlaces.isEmpty();
  }

  @Override
  public final boolean isForwardAvailable() {
    return !myForwardPlaces.isEmpty();
  }

  @Override
  public final void navigatePreviousChange() {
    removeInvalidFilesFromStacks();
    if (myCurrentIndex == 0) return;
    PlaceInfo currentPlace = getCurrentPlaceInfo();
    for (int i = myCurrentIndex - 1; i >= 0; i--) {
      PlaceInfo info = myChangePlaces.get(i);
      if (currentPlace == null || !isSame(currentPlace, info)) {
        executeCommand(() -> gotoPlaceInfo(info), "", null);
        myCurrentIndex = i;
        break;
      }
    }
  }

  @Override
  @Nonnull
  public List<PlaceInfo> getBackPlaces() {
    return ContainerUtil.immutableList(myBackPlaces);
  }

  @Override
  public List<PlaceInfo> getChangePlaces() {
    return ContainerUtil.immutableList(myChangePlaces);
  }

  @Override
  public void removeBackPlace(@Nonnull PlaceInfo placeInfo) {
    removePlaceInfo(placeInfo, myBackPlaces, false);
  }

  @Override
  public void removeChangePlace(@Nonnull PlaceInfo placeInfo) {
    removePlaceInfo(placeInfo, myChangePlaces, true);
  }

  private void removePlaceInfo(@Nonnull PlaceInfo placeInfo, @Nonnull LinkedList<PlaceInfo> places, boolean changed) {
    boolean removed = places.remove(placeInfo);
    if (removed) {
      myProject.getMessageBus().syncPublisher(RecentPlacesListener.class).recentPlaceRemoved(placeInfo, changed);
    }
  }

  @Override
  public final boolean isNavigatePreviousChangeAvailable() {
    return myCurrentIndex > 0;
  }

  void removeInvalidFilesFromStacks() {
    removeInvalidFilesFrom(myBackPlaces);

    removeInvalidFilesFrom(myForwardPlaces);
    if (removeInvalidFilesFrom(myChangePlaces)) {
      myCurrentIndex = myChangePlaces.size();
    }
  }

  @Override
  public void navigateNextChange() {
    removeInvalidFilesFromStacks();
    if (myCurrentIndex >= myChangePlaces.size()) return;
    PlaceInfo currentPlace = getCurrentPlaceInfo();
    for (int i = myCurrentIndex; i < myChangePlaces.size(); i++) {
      PlaceInfo info = myChangePlaces.get(i);
      if (currentPlace == null || !isSame(currentPlace, info)) {
        executeCommand(() -> gotoPlaceInfo(info), "", null);
        myCurrentIndex = i + 1;
        break;
      }
    }
  }

  @Override
  public boolean isNavigateNextChangeAvailable() {
    return myCurrentIndex < myChangePlaces.size();
  }

  private static boolean removeInvalidFilesFrom(@Nonnull List<PlaceInfo> backPlaces) {
    return backPlaces.removeIf(info -> (info.myFile instanceof SkipFromDocumentHistory) || !info.myFile.isValid());
  }

  @Override
  public void gotoPlaceInfo(@Nonnull PlaceInfo info) {
    final boolean wasActive = ToolWindowManager.getInstance(myProject).isEditorComponentActive();
    FileEditorWindow wnd = info.getWindow();
    FileEditorManagerEx editorManager = getFileEditorManager();
    final Pair<FileEditor[], FileEditorProvider[]> editorsWithProviders =
            wnd != null && wnd.isValid() ? editorManager.openFileWithProviders(info.getFile(), wasActive, wnd) : editorManager.openFileWithProviders(info.getFile(), wasActive, false);

    editorManager.setSelectedEditor(info.getFile(), info.getEditorTypeId());

    final FileEditor[] editors = editorsWithProviders.getFirst();
    final FileEditorProvider[] providers = editorsWithProviders.getSecond();
    for (int i = 0; i < editors.length; i++) {
      String typeId = providers[i].getEditorTypeId();
      if (typeId.equals(info.getEditorTypeId())) {
        editors[i].setState(info.getNavigationState());
      }
    }
  }

  /**
   * @return currently selected FileEditor or null.
   */
  @Nullable
  protected FileEditorWithProvider getSelectedEditor() {
    FileEditorManagerEx editorManager = getFileEditorManager();
    VirtualFile file = editorManager.getCurrentFile();
    return file == null ? null : editorManager.getSelectedEditorWithProvider(file);
  }

  protected PlaceInfo createPlaceInfo(@Nonnull final FileEditor fileEditor, final FileEditorProvider fileProvider) {
    if (!fileEditor.isValid()) {
      return null;
    }

    FileEditorManagerEx editorManager = getFileEditorManager();
    final VirtualFile file = editorManager.getFile(fileEditor);
    LOG.assertTrue(file != null);
    FileEditorState state = fileEditor.getState(FileEditorStateLevel.NAVIGATION);

    return new PlaceInfo(file, state, fileProvider.getEditorTypeId(), editorManager.getCurrentWindow(), getCaretPosition(fileEditor), System.currentTimeMillis());
  }

  @Nullable
  private static RangeMarker getCaretPosition(@Nonnull FileEditor fileEditor) {
    if (!(fileEditor instanceof TextEditor)) {
      return null;
    }

    Editor editor = ((TextEditor)fileEditor).getEditor();
    int offset = editor.getCaretModel().getOffset();

    return editor.getDocument().createRangeMarker(offset, offset);
  }

  private void putLastOrMerge(@Nonnull PlaceInfo next, int limit, boolean isChanged) {
    LinkedList<PlaceInfo> list = isChanged ? myChangePlaces : myBackPlaces;
    MessageBus messageBus = myProject.getMessageBus();
    RecentPlacesListener listener = messageBus.syncPublisher(RecentPlacesListener.class);
    if (!list.isEmpty()) {
      PlaceInfo prev = list.getLast();
      if (isSame(prev, next)) {
        PlaceInfo removed = list.removeLast();
        listener.recentPlaceRemoved(removed, isChanged);
      }
    }

    list.add(next);
    listener.recentPlaceAdded(next, isChanged);
    if (list.size() > limit) {
      PlaceInfo first = list.removeFirst();
      listener.recentPlaceRemoved(first, isChanged);
    }
  }

  private FileDocumentManager getFileDocumentManager() {
    if (myFileDocumentManager == null) {
      myFileDocumentManager = FileDocumentManager.getInstance();
    }
    return myFileDocumentManager;
  }

  public static final class PlaceInfo {
    private final VirtualFile myFile;
    private final FileEditorState myNavigationState;
    private final String myEditorTypeId;
    private final Reference<FileEditorWindow> myWindow;
    @Nullable
    private final RangeMarker myCaretPosition;
    private final long myTimeStamp;

    public PlaceInfo(@Nonnull VirtualFile file, @Nonnull FileEditorState navigationState, @Nonnull String editorTypeId, @Nullable FileEditorWindow window, @Nullable RangeMarker caretPosition) {
      myNavigationState = navigationState;
      myFile = file;
      myEditorTypeId = editorTypeId;
      myWindow = new WeakReference<>(window);
      myCaretPosition = caretPosition;
      myTimeStamp = -1;
    }

    public PlaceInfo(@Nonnull VirtualFile file,
                     @Nonnull FileEditorState navigationState,
                     @Nonnull String editorTypeId,
                     @Nullable FileEditorWindow window,
                     @Nullable RangeMarker caretPosition,
                     long stamp) {
      myNavigationState = navigationState;
      myFile = file;
      myEditorTypeId = editorTypeId;
      myWindow = new WeakReference<>(window);
      myCaretPosition = caretPosition;
      myTimeStamp = stamp;
    }

    public FileEditorWindow getWindow() {
      return myWindow.get();
    }

    @Nonnull
    public FileEditorState getNavigationState() {
      return myNavigationState;
    }

    @Nonnull
    public VirtualFile getFile() {
      return myFile;
    }

    @Nonnull
    public String getEditorTypeId() {
      return myEditorTypeId;
    }

    @Override
    public String toString() {
      return getFile().getName() + " " + getNavigationState();
    }

    @Nullable
    public RangeMarker getCaretPosition() {
      return myCaretPosition;
    }

    public long getTimeStamp() {
      return myTimeStamp;
    }
  }

  @Override
  public final void dispose() {
    myLastGroupId = null;
  }

  protected void executeCommand(Runnable runnable, String name, Object groupId) {
    CommandProcessor.getInstance().executeCommand(myProject, runnable, name, groupId);
  }

  public static boolean isSame(@Nonnull PlaceInfo first, @Nonnull PlaceInfo second) {
    if (first.getFile().equals(second.getFile())) {
      FileEditorState firstState = first.getNavigationState();
      FileEditorState secondState = second.getNavigationState();
      return firstState.equals(secondState) || firstState.canBeMergedWith(secondState, FileEditorStateLevel.NAVIGATION);
    }

    return false;
  }

}
