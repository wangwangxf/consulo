// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.codeEditor.impl;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.FoldRegionImpl;
import com.intellij.openapi.editor.impl.FoldRegionsTree;
import com.intellij.openapi.editor.impl.HardReferencingRangeMarkerTree;
import com.intellij.util.containers.ContainerUtil;
import consulo.application.ApplicationManager;
import consulo.codeEditor.*;
import consulo.codeEditor.EditorColors;
import consulo.colorScheme.TextAttributes;
import consulo.component.util.ModificationTracker;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.document.event.DocumentEvent;
import consulo.document.impl.DocumentEx;
import consulo.document.impl.DocumentUtil;
import consulo.document.impl.EditorDocumentPriorities;
import consulo.document.impl.RangeMarkerTree;
import consulo.document.impl.event.PrioritizedInternalDocumentListener;
import consulo.logging.Logger;
import consulo.util.collection.MultiMap;
import consulo.util.collection.Sets;
import consulo.util.dataholder.Key;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Common part from desktop folding model
 */
public class CodeEditorFoldingModelBase extends InlayModel.SimpleAdapter implements FoldingModelEx, PrioritizedInternalDocumentListener, Dumpable, ModificationTracker {
  private static final Logger LOG = Logger.getInstance(CodeEditorFoldingModelBase.class);

  public static final Key<Boolean> SELECT_REGION_ON_CARET_NEARBY = Key.create("select.region.on.caret.nearby");

  private static final Key<SavedCaretPosition> SAVED_CARET_POSITION = Key.create("saved.position.before.folding");
  private static final Key<Boolean> MARK_FOR_UPDATE = Key.create("marked.for.position.update");

  private final List<FoldingListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean myIsFoldingEnabled;
  protected final CodeEditorBase myEditor;
  private final RangeMarkerTree<FoldRegionImpl> myRegionTree;
  private final FoldRegionsTree myFoldTree;
  private TextAttributes myFoldTextAttributes;
  protected boolean myIsBatchFoldingProcessing;
  private boolean myDoNotCollapseCaret;
  protected boolean myFoldRegionsProcessed;

  private boolean myDisableScrollingPositionAdjustment;
  private final MultiMap<FoldingGroup, FoldRegion> myGroups = new MultiMap<>();
  private boolean myDocumentChangeProcessed = true;
  private final AtomicLong myExpansionCounter = new AtomicLong();
  private final EditorScrollingPositionKeeper myScrollingPositionKeeper;

  protected CodeEditorFoldingModelBase(@Nonnull CodeEditorBase editor) {
    myEditor = editor;
    myIsFoldingEnabled = true;
    myIsBatchFoldingProcessing = false;
    myDoNotCollapseCaret = false;
    myRegionTree = new MyMarkerTree(editor.getDocument());
    myFoldTree = new FoldRegionsTree(myRegionTree) {
      @Override
      protected boolean isFoldingEnabled() {
        return CodeEditorFoldingModelBase.this.isFoldingEnabled();
      }

      @Override
      protected boolean hasBlockInlays() {
        return myEditor.getInlayModel().hasBlockElements();
      }

      @Override
      protected int getBlockInlaysHeight(int startOffset, int endOffset) {
        return EditorUtil.getTotalInlaysHeight(myEditor.getInlayModel().getBlockElementsInRange(startOffset, endOffset));
      }
    };
    myFoldRegionsProcessed = false;

    myScrollingPositionKeeper = new EditorScrollingPositionKeeper(editor);
    Disposer.register(editor.getDisposable(), myScrollingPositionKeeper);

    refreshSettings();
  }

  public void onPlaceholderTextChanged(FoldRegionImpl region) {
    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be changed inside batchFoldProcessing() only");
    }
    myFoldRegionsProcessed = true;
    // ((DesktopEditorImpl)myEditor).myView.invalidateFoldRegionLayout(region);
    notifyListenersOnFoldRegionStateChange(region);
  }

  @Override
  @Nonnull
  public List<FoldRegion> getGroupedRegions(@Nonnull FoldingGroup group) {
    return (List<FoldRegion>)myGroups.get(group);
  }

  @Override
  public void clearDocumentRangesModificationStatus() {
    assertIsDispatchThreadForEditor();
    myFoldTree.clearDocumentRangesModificationStatus();
  }

  @Override
  public boolean hasDocumentRegionChangedFor(@Nonnull FoldRegion region) {
    assertReadAccess();
    return region instanceof FoldRegionImpl && ((FoldRegionImpl)region).hasDocumentRegionChanged();
  }

  public int getEndOffset(@Nonnull FoldingGroup group) {
    final List<FoldRegion> regions = getGroupedRegions(group);
    int endOffset = 0;
    for (FoldRegion region : regions) {
      if (region.isValid()) {
        endOffset = Math.max(endOffset, region.getEndOffset());
      }
    }
    return endOffset;
  }

  public void refreshSettings() {
    myFoldTextAttributes = myEditor.getColorsScheme().getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES);
  }

  @Override
  public boolean isFoldingEnabled() {
    return myIsFoldingEnabled;
  }

  @Override
  public boolean isOffsetCollapsed(int offset) {
    assertReadAccess();
    return getCollapsedRegionAtOffset(offset) != null;
  }

  private boolean isOffsetInsideCollapsedRegion(int offset) {
    assertReadAccess();
    FoldRegion region = getCollapsedRegionAtOffset(offset);
    return region != null && region.getStartOffset() < offset;
  }

  private static void assertIsDispatchThreadForEditor() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  private static void assertReadAccess() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
  }

  private static void assertOurRegion(FoldRegion region) {
    if (!(region instanceof FoldRegionImpl)) {
      throw new IllegalArgumentException("Only regions created by this instance of FoldingModel are accepted");
    }
  }

  @Override
  public void setFoldingEnabled(boolean isEnabled) {
    assertIsDispatchThreadForEditor();
    myIsFoldingEnabled = isEnabled;
  }

  @Override
  public FoldRegion addFoldRegion(int startOffset, int endOffset, @Nonnull String placeholderText) {
    return createFoldRegion(startOffset, endOffset, placeholderText, null, false);
  }

  @Override
  public void runBatchFoldingOperation(@Nonnull Runnable operation) {
    runBatchFoldingOperation(operation, false, true);
  }

  @Override
  public void runBatchFoldingOperation(@Nonnull Runnable operation, boolean moveCaret) {
    runBatchFoldingOperation(operation, false, moveCaret);
  }

  private void runBatchFoldingOperation(@Nonnull Runnable operation, final boolean dontCollapseCaret, final boolean moveCaret) {
    assertIsDispatchThreadForEditor();
    boolean oldDontCollapseCaret = myDoNotCollapseCaret;
    myDoNotCollapseCaret |= dontCollapseCaret;
    boolean oldBatchFlag = myIsBatchFoldingProcessing;
    if (!oldBatchFlag) {
      myEditor.getScrollingModel().finishAnimation();
      myScrollingPositionKeeper.savePosition();
      myDisableScrollingPositionAdjustment = false;
    }

    myIsBatchFoldingProcessing = true;
    try {
      operation.run();
    }
    finally {
      if (!oldBatchFlag) {
        myIsBatchFoldingProcessing = false;
        if (myFoldRegionsProcessed) {
          notifyBatchFoldingProcessingDone(moveCaret);
          myFoldRegionsProcessed = false;
        }
      }
      myDoNotCollapseCaret = oldDontCollapseCaret;
    }
  }

  @Override
  public void runBatchFoldingOperationDoNotCollapseCaret(@Nonnull final Runnable operation) {
    runBatchFoldingOperation(operation, true, true);
  }

  /**
   * Disables scrolling position adjustment after batch folding operation is finished.
   * Should be called from inside batch operation runnable.
   */
  public void disableScrollingPositionAdjustment() {
    myDisableScrollingPositionAdjustment = true;
  }

  @Override
  @Nonnull
  public FoldRegion[] getAllFoldRegions() {
    assertReadAccess();
    return myFoldTree.fetchAllRegions();
  }

  @Override
  @Nullable
  public FoldRegion getCollapsedRegionAtOffset(int offset) {
    return myFoldTree.fetchOutermost(offset);
  }

  @Nullable
  @Override
  public FoldRegion getFoldRegion(int startOffset, int endOffset) {
    assertReadAccess();
    return myFoldTree.getRegionAt(startOffset, endOffset);
  }

  @Override
  @Nullable
  public FoldRegion getFoldingPlaceholderAt(@Nonnull Point p) {
    assertReadAccess();
    LogicalPosition pos = myEditor.xyToLogicalPosition(p);
    int line = pos.line;

    if (line >= myEditor.getDocument().getLineCount()) return null;

    int offset = myEditor.logicalPositionToOffset(pos);

    return myFoldTree.fetchOutermost(offset);
  }

  @Override
  public void removeFoldRegion(@Nonnull final FoldRegion region) {
    assertIsDispatchThreadForEditor();
    assertOurRegion(region);

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
    }

    ((FoldRegionImpl)region).setExpanded(true, false);
    notifyListenersOnFoldRegionStateChange(region);
    notifyListenersOnFoldRegionRemove(region);

    myFoldRegionsProcessed = true;
    region.dispose();
  }

  public void removeRegionFromTree(@Nonnull FoldRegionImpl region) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!((CodeEditorFoldingModelBase)myEditor.getFoldingModel()).isInBatchFoldingOperation()) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
    }
    myFoldRegionsProcessed = true;
    myRegionTree.removeInterval(region);
    removeRegionFromGroup(region);
  }

  void removeRegionFromGroup(@Nonnull FoldRegion region) {
    myGroups.remove(region.getGroup(), region);
  }

  public void dispose() {
    doClearFoldRegions();
    myRegionTree.dispose(myEditor.getDocument());
  }

  @Override
  public void clearFoldRegions() {
    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
      return;
    }
    FoldRegion[] regions = getAllFoldRegions();
    for (FoldRegion region : regions) {
      if (!region.isExpanded()) notifyListenersOnFoldRegionStateChange(region);
      notifyListenersOnFoldRegionRemove(region);
      region.dispose();
    }
    doClearFoldRegions();
  }

  private void doClearFoldRegions() {
    myGroups.clear();
    myFoldTree.clear();
  }

  public void expandFoldRegion(@Nonnull FoldRegion region, boolean notify) {
    assertIsDispatchThreadForEditor();
    if (region.isExpanded() || region.shouldNeverExpand()) return;

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be collapsed or expanded inside batchFoldProcessing() only.");
    }

    for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
      SavedCaretPosition savedPosition = caret.getUserData(SAVED_CARET_POSITION);
      if (savedPosition != null && savedPosition.isUpToDate(myEditor)) {
        int savedOffset = myEditor.logicalPositionToOffset(savedPosition.position);

        FoldRegion[] allCollapsed = myFoldTree.fetchCollapsedAt(savedOffset);
        if (allCollapsed.length == 1 && allCollapsed[0] == region) {
          caret.putUserData(MARK_FOR_UPDATE, Boolean.TRUE);
        }
      }
      else if (caret.getOffset() == region.getStartOffset()) {
        caret.putUserData(MARK_FOR_UPDATE, Boolean.TRUE);
        caret.putUserData(SAVED_CARET_POSITION, new SavedCaretPosition(caret));
      }
    }

    myFoldRegionsProcessed = true;
    myExpansionCounter.incrementAndGet();
    ((FoldRegionImpl)region).setExpandedInternal(true);
    if (notify) notifyListenersOnFoldRegionStateChange(region);
  }

  public void collapseFoldRegion(@Nonnull FoldRegion region, boolean notify) {
    assertIsDispatchThreadForEditor();
    if (!region.isExpanded()) return;

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be collapsed or expanded inside batchFoldProcessing() only.");
    }

    List<Caret> carets = myEditor.getCaretModel().getAllCarets();
    if (myDoNotCollapseCaret) {
      for (Caret caret : carets) {
        if (FoldRegionsTree.containsStrict(region, caret.getOffset())) return;
      }
    }
    for (Caret caret : carets) {
      if (FoldRegionsTree.containsStrict(region, caret.getOffset())) {
        SavedCaretPosition savedPosition = caret.getUserData(SAVED_CARET_POSITION);
        if (savedPosition == null || !savedPosition.isUpToDate(myEditor)) {
          caret.putUserData(SAVED_CARET_POSITION, new SavedCaretPosition(caret));
        }
      }
    }

    myFoldRegionsProcessed = true;
    ((FoldRegionImpl)region).setExpandedInternal(false);
    if (notify) notifyListenersOnFoldRegionStateChange(region);
  }

  protected void notifyBatchFoldingProcessingDoneToEditor() {

  }

  private void notifyBatchFoldingProcessingDone(final boolean moveCaretFromCollapsedRegion) {
    clearCachedValues();

    for (FoldingListener listener : myListeners) {
      listener.onFoldProcessingEnd();
    }

    notifyBatchFoldingProcessingDoneToEditor();

    myEditor.getCaretModel().runBatchCaretOperation(() -> {
      for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
        LogicalPosition positionToUse = null;
        int offsetToUse = -1;

        SavedCaretPosition savedPosition = caret.getUserData(SAVED_CARET_POSITION);
        boolean markedForUpdate = caret.getUserData(MARK_FOR_UPDATE) != null;

        if (savedPosition != null && savedPosition.isUpToDate(myEditor)) {
          int savedOffset = myEditor.logicalPositionToOffset(savedPosition.position);
          FoldRegion collapsedAtSaved = myFoldTree.fetchOutermost(savedOffset);
          if (collapsedAtSaved == null) {
            positionToUse = savedPosition.position;
          }
          else {
            offsetToUse = collapsedAtSaved.getStartOffset();
          }
        }

        if ((markedForUpdate || moveCaretFromCollapsedRegion) && caret.isUpToDate()) {
          if (offsetToUse >= 0) {
            caret.moveToOffset(offsetToUse);
          }
          else if (positionToUse != null) {
            caret.moveToLogicalPosition(positionToUse);
          }
          else {
            ((CodeEditorCaretBase)caret).updateVisualPosition();
          }
        }

        caret.putUserData(SAVED_CARET_POSITION, savedPosition);
        caret.putUserData(MARK_FOR_UPDATE, null);

        int selectionStart = caret.getSelectionStart();
        int selectionEnd = caret.getSelectionEnd();
        if (isOffsetInsideCollapsedRegion(selectionStart) || isOffsetInsideCollapsedRegion(selectionEnd)) {
          caret.removeSelection();
        }
        else if (selectionStart < myEditor.getDocument().getTextLength()) {
          caret.setSelection(selectionStart, selectionEnd);
        }
      }
    });
    if (!myDisableScrollingPositionAdjustment) myScrollingPositionKeeper.restorePosition(true);
  }

  @Override
  public void rebuild() {
    if (!myEditor.getDocument().isInBulkUpdate()) {
      myFoldTree.rebuild();
    }
  }

  public boolean isInBatchFoldingOperation() {
    return myIsBatchFoldingProcessing;
  }

  private void updateCachedOffsets() {
    myFoldTree.updateCachedOffsets();
  }

  public int getFoldedLinesCountBefore(int offset) {
    if (!myDocumentChangeProcessed && myEditor.getDocument().isInEventsHandling()) {
      // There is a possible case that this method is called on document update before fold regions are recalculated.
      // We return zero in such situations then.
      return 0;
    }
    return myFoldTree.getFoldedLinesCountBefore(offset);
  }

  public int getTotalNumberOfFoldedLines() {
    if (!myDocumentChangeProcessed && myEditor.getDocument().isInEventsHandling()) {
      // There is a possible case that this method is called on document update before fold regions are recalculated.
      // We return zero in such situations then.
      return 0;
    }
    return myFoldTree.getTotalNumberOfFoldedLines();
  }

  public int getHeightOfFoldedBlockInlaysBefore(int offset) {
    return myFoldTree.getHeightOfFoldedBlockInlaysBefore(offset);
  }

  public int getTotalHeightOfFoldedBlockInlays() {
    return myFoldTree.getTotalHeightOfFoldedBlockInlays();
  }

  @Override
  @Nullable
  public FoldRegion[] fetchTopLevel() {
    return myFoldTree.fetchTopLevel();
  }

  @Nonnull
  public FoldRegion[] fetchCollapsedAt(int offset) {
    return myFoldTree.fetchCollapsedAt(offset);
  }

  @Override
  public boolean intersectsRegion(int startOffset, int endOffset) {
    return myFoldTree.intersectsRegion(startOffset, endOffset);
  }

  @Nullable
  public FoldRegion[] fetchVisible() {
    return myFoldTree.fetchVisible();
  }

  @Override
  public int getLastCollapsedRegionBefore(int offset) {
    return myFoldTree.getLastTopLevelIndexBefore(offset);
  }

  @Override
  public TextAttributes getPlaceholderAttributes() {
    return myFoldTextAttributes;
  }

  public void flushCaretPosition(@Nonnull Caret caret) {
    caret.putUserData(SAVED_CARET_POSITION, null);
  }

  public void onBulkDocumentUpdateStarted() {
    clearCachedValues();
  }

  public void clearCachedValues() {
    myFoldTree.clearCachedValues();
  }

  public void onBulkDocumentUpdateFinished() {
    myFoldTree.rebuild();
  }

  @Override
  public void beforeDocumentChange(@Nonnull DocumentEvent event) {
    if (myIsBatchFoldingProcessing) LOG.error("Document changes are not allowed during batch folding update");
    myDocumentChangeProcessed = false;
  }

  @Override
  public void documentChanged(@Nonnull DocumentEvent event) {
    try {
      if (!((DocumentEx)event.getDocument()).isInBulkUpdate()) {
        updateCachedOffsets();
      }
    }
    finally {
      myDocumentChangeProcessed = true;
    }
  }

  @Override
  public void moveTextHappened(@Nonnull Document document, int start, int end, int base) {
    if (!myEditor.getDocument().isInBulkUpdate()) {
      myFoldTree.rebuild();
    }
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.FOLD_MODEL;
  }

  @Override
  public void onUpdated(@Nonnull Inlay inlay) {
    Inlay.Placement placement = inlay.getPlacement();
    if (placement == Inlay.Placement.ABOVE_LINE || placement == Inlay.Placement.BELOW_LINE) myFoldTree.clearCachedInlayValues();
  }

  @Nullable
  @Override
  public FoldRegion createFoldRegion(int startOffset, int endOffset, @Nonnull String placeholder, @Nullable FoldingGroup group, boolean neverExpands) {
    assertIsDispatchThreadForEditor();
    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
      return null;
    }
    if (!isFoldingEnabled() || startOffset >= endOffset ||
        DocumentUtil.isInsideSurrogatePair(myEditor.getDocument(), startOffset) ||
        DocumentUtil.isInsideSurrogatePair(myEditor.getDocument(), endOffset) ||
        !myFoldTree.checkIfValidToCreate(startOffset, endOffset)) return null;

    FoldRegionImpl region = new FoldRegionImpl(myEditor, startOffset, endOffset, placeholder, group, neverExpands);
    myRegionTree.addInterval(region, startOffset, endOffset, false, false, false, 0);
    LOG.assertTrue(region.isValid());
    myFoldRegionsProcessed = true;
    if (group != null) {
      myGroups.putValue(group, region);
    }
    notifyListenersOnFoldRegionStateChange(region);
    LOG.assertTrue(region.isValid());
    return region;
  }

  @Override
  public void addListener(@Nonnull final FoldingListener listener, @Nonnull Disposable parentDisposable) {
    myListeners.add(listener);
    Disposer.register(parentDisposable, () -> myListeners.remove(listener));
  }

  protected void notifyListenersOnFoldRegionStateChange(@Nonnull FoldRegion foldRegion) {
    for (FoldingListener listener : myListeners) {
      listener.onFoldRegionStateChange(foldRegion);
    }
  }

  private void notifyListenersOnFoldRegionRemove(@Nonnull FoldRegion foldRegion) {
    for (FoldingListener listener : myListeners) {
      listener.beforeFoldRegionRemoved(foldRegion);
    }
  }

  @Nonnull
  @Override
  public String dumpState() {
    return Arrays.toString(myFoldTree.fetchTopLevel());
  }

  @Override
  public String toString() {
    return dumpState();
  }

  @Override
  public long getModificationCount() {
    return myExpansionCounter.get();
  }

  @TestOnly
  public void validateState() {
    if (myEditor.getDocument().isInBulkUpdate()) return;

    FoldRegion[] allFoldRegions = getAllFoldRegions();
    boolean[] invisibleRegions = new boolean[allFoldRegions.length];
    for (int i = 0; i < allFoldRegions.length; i++) {
      FoldRegion r1 = allFoldRegions[i];
      LOG.assertTrue(r1.isValid() && !DocumentUtil.isInsideSurrogatePair(myEditor.getDocument(), r1.getStartOffset()) && !DocumentUtil.isInsideSurrogatePair(myEditor.getDocument(), r1.getEndOffset()),
                     "Invalid region");
      for (int j = i + 1; j < allFoldRegions.length; j++) {
        FoldRegion r2 = allFoldRegions[j];
        int r1s = r1.getStartOffset();
        int r1e = r1.getEndOffset();
        int r2s = r2.getStartOffset();
        int r2e = r2.getEndOffset();
        LOG.assertTrue(r1s < r2s && (r1e <= r2s || r1e >= r2e) || r1s == r2s && r1e != r2e || r1s > r2s && r1s < r2e && r1e <= r2e || r1s >= r2e, "Disallowed relative position of regions");
        if (!r1.isExpanded() && r1s <= r2s && r1e >= r2e) invisibleRegions[j] = true;
        if (!r2.isExpanded() && r2s <= r1s && r2e >= r1e) invisibleRegions[i] = true;
      }
    }
    Set<FoldRegion> visibleRegions = Sets.newHashSet(FoldRegionsTree.OFFSET_BASED_HASHING_STRATEGY);
    List<FoldRegion> topLevelRegions = new ArrayList<>();
    for (int i = 0; i < allFoldRegions.length; i++) {
      if (!invisibleRegions[i]) {
        FoldRegion region = allFoldRegions[i];
        LOG.assertTrue(visibleRegions.add(region), "Duplicate visible regions");
        if (!region.isExpanded()) topLevelRegions.add(region);
      }
    }
    Collections.sort(topLevelRegions, Comparator.comparingInt(r -> r.getStartOffset()));

    FoldRegion[] actualVisibles = fetchVisible();
    if (actualVisibles != null) {
      for (FoldRegion r : actualVisibles) {
        LOG.assertTrue(visibleRegions.remove(r), "Unexpected visible region");
      }
      LOG.assertTrue(visibleRegions.isEmpty(), "Missing visible region");
    }

    FoldRegion[] actualTopLevels = fetchTopLevel();
    if (actualTopLevels != null) {
      LOG.assertTrue(actualTopLevels.length == topLevelRegions.size(), "Wrong number of top-level regions");
      for (int i = 0; i < actualTopLevels.length; i++) {
        LOG.assertTrue(FoldRegionsTree.OFFSET_BASED_HASHING_STRATEGY.equals(actualTopLevels[i], topLevelRegions.get(i)), "Unexpected top-level region");
      }
    }
  }

  private static class SavedCaretPosition {
    private final LogicalPosition position;
    private final long docStamp;

    private SavedCaretPosition(Caret caret) {
      position = caret.getLogicalPosition();
      docStamp = caret.getEditor().getDocument().getModificationStamp();
    }

    private boolean isUpToDate(Editor editor) {
      return docStamp == editor.getDocument().getModificationStamp();
    }
  }

  private class MyMarkerTree extends HardReferencingRangeMarkerTree<FoldRegionImpl> {
    private boolean inCollectCall;

    private MyMarkerTree(Document document) {
      super(document);
    }

    @Nonnull
    private FoldRegionImpl getRegion(@Nonnull IntervalNode<FoldRegionImpl> node) {
      assert node.intervals.size() == 1;
      FoldRegionImpl region = node.intervals.get(0).get();
      assert region != null;
      return region;
    }

    @Nonnull
    @Override
    protected Node<FoldRegionImpl> createNewNode(@Nonnull FoldRegionImpl key, int start, int end, boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
      return new Node<>(this, key, start, end, greedyToLeft, greedyToRight, stickingToRight) {
        @Override
        public void onRemoved() {
          for (Supplier<FoldRegionImpl> getter : intervals) {
            removeRegionFromGroup(getter.get());
          }
        }

        @Override
        public void addIntervalsFrom(@Nonnull IntervalNode<FoldRegionImpl> otherNode) {
          FoldRegionImpl region = getRegion(this);
          FoldRegionImpl otherRegion = getRegion(otherNode);
          if (otherRegion.mySizeBeforeUpdate > region.mySizeBeforeUpdate) {
            setNode(region, null);
            removeRegionFromGroup(region);
            removeIntervalInternal(0);
            super.addIntervalsFrom(otherNode);
          }
          else {
            otherNode.setValid(false);
            ((RMNode)otherNode).onRemoved();
          }
        }
      };
    }

    @Override
    public boolean collectAffectedMarkersAndShiftSubtrees(@Nullable IntervalNode<FoldRegionImpl> root, @Nonnull DocumentEvent e, @Nonnull List<? super IntervalNode<FoldRegionImpl>> affected) {
      if (inCollectCall) return super.collectAffectedMarkersAndShiftSubtrees(root, e, affected);
      inCollectCall = true;
      boolean result;
      try {
        result = super.collectAffectedMarkersAndShiftSubtrees(root, e, affected);
      }
      finally {
        inCollectCall = false;
      }
      if (e.getOldLength() > 0 /* document change can cause regions to become equal*/) {
        for (Object o : affected) {
          //noinspection unchecked
          Node<FoldRegionImpl> node = (Node<FoldRegionImpl>)o;
          FoldRegionImpl region = getRegion(node);
          // region with the largest metric value is kept when several regions become identical after document change
          // we want the largest collapsed region to survive
          region.mySizeBeforeUpdate = region.isExpanded() ? 0 : node.intervalEnd() - node.intervalStart();
        }
      }
      return result;
    }
  }
}
