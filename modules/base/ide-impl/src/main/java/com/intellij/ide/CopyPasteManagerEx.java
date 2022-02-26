// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import consulo.application.ui.UISettings;
import com.intellij.openapi.editor.CaretStateTransferableData;
import consulo.document.Document;
import consulo.ui.ex.awt.CopyPasteManager;
import com.intellij.openapi.ide.CutElementMarker;
import com.intellij.openapi.ide.KillRingTransferable;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.EventDispatcher;
import consulo.disposer.Disposable;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import jakarta.inject.Singleton;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class CopyPasteManagerEx extends CopyPasteManager implements ClipboardOwner {
  private final List<Transferable> myData = new ArrayList<>();
  private final EventDispatcher<ContentChangedListener> myDispatcher = EventDispatcher.create(ContentChangedListener.class);
  private final ClipboardSynchronizer myClipboardSynchronizer;
  private boolean myOwnContent;

  public static CopyPasteManagerEx getInstanceEx() {
    return (CopyPasteManagerEx)getInstance();
  }

  @Inject
  public CopyPasteManagerEx(ClipboardSynchronizer clipboardSynchronizer) {
    myClipboardSynchronizer = clipboardSynchronizer;
  }

  @Override
  public void lostOwnership(Clipboard clipboard, Transferable contents) {
    myOwnContent = false;
    myClipboardSynchronizer.resetContent();
    fireContentChanged(contents, null);
  }

  private void fireContentChanged(@Nullable Transferable oldContent, @Nullable Transferable newContent) {
    myDispatcher.getMulticaster().contentChanged(oldContent, newContent);
  }

  @Override
  public void addContentChangedListener(@Nonnull ContentChangedListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void addContentChangedListener(@Nonnull final ContentChangedListener listener, @Nonnull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  @Override
  public void removeContentChangedListener(@Nonnull ContentChangedListener listener) {
    myDispatcher.removeListener(listener);
  }

  @Override
  public boolean areDataFlavorsAvailable(@Nonnull DataFlavor... flavors) {
    return flavors.length > 0 && myClipboardSynchronizer.areDataFlavorsAvailable(flavors);
  }

  @Override
  public void setContents(@Nonnull Transferable content) {
    Transferable oldContent = myOwnContent && !myData.isEmpty() ? myData.get(0) : null;

    Transferable contentToUse = addNewContentToStack(content);
    setSystemClipboardContent(contentToUse);

    fireContentChanged(oldContent, contentToUse);
  }

  @Override
  public boolean isCutElement(@Nullable final Object element) {
    for (CutElementMarker marker : CutElementMarker.EP_NAME.getExtensions()) {
      if (marker.isCutElement(element)) return true;
    }
    return false;
  }

  @Override
  public void stopKillRings() {
    for (Transferable data : myData) {
      if (data instanceof KillRingTransferable) {
        ((KillRingTransferable)data).setReadyToCombine(false);
      }
    }
  }

  private void setSystemClipboardContent(Transferable content) {
    myClipboardSynchronizer.setContent(content, this);
    myOwnContent = true;
  }

  /**
   * Stores given content within the current manager. It is merged with already stored ones
   * if necessary (see {@link KillRingTransferable}).
   *
   * @param content content to store
   * @return content that is either the given one or the one that was assembled from it and already stored one
   */
  @Nonnull
  private Transferable addNewContentToStack(@Nonnull Transferable content) {
    try {
      String clipString = getStringContent(content);
      if (clipString == null) {
        return content;
      }

      if (content instanceof KillRingTransferable) {
        KillRingTransferable killRingContent = (KillRingTransferable)content;
        if (killRingContent.isReadyToCombine() && !myData.isEmpty()) {
          Transferable prev = myData.get(0);
          if (prev instanceof KillRingTransferable) {
            Transferable merged = merge(killRingContent, (KillRingTransferable)prev);
            if (merged != null) {
              myData.set(0, merged);
              return merged;
            }
          }
        }
        if (killRingContent.isReadyToCombine()) {
          addToTheTopOfTheStack(killRingContent);
          return killRingContent;
        }
      }

      CaretStateTransferableData caretData = CaretStateTransferableData.getFrom(content);
      for (int i = 0; i < myData.size(); i++) {
        Transferable old = myData.get(i);
        if (clipString.equals(getStringContent(old)) && CaretStateTransferableData.areEquivalent(caretData, CaretStateTransferableData.getFrom(old))) {
          myData.remove(i);
          myData.add(0, content);
          return content;
        }
      }

      addToTheTopOfTheStack(content);
    }
    catch (UnsupportedFlavorException | IOException ignore) {
    }
    return content;
  }

  private void addToTheTopOfTheStack(@Nonnull Transferable content) {
    myData.add(0, content);
    deleteAfterAllowedMaximum();
  }

  /**
   * Merges given new data with the given old one and returns merge result in case of success.
   *
   * @param newData new data to merge
   * @param oldData old data to merge
   * @return merge result of the given data if possible; {@code null} otherwise
   * @throws IOException                as defined by {@link Transferable#getTransferData(DataFlavor)}
   * @throws UnsupportedFlavorException as defined by {@link Transferable#getTransferData(DataFlavor)}
   */
  @Nullable
  private static Transferable merge(@Nonnull KillRingTransferable newData, @Nonnull KillRingTransferable oldData) throws IOException, UnsupportedFlavorException {
    if (!oldData.isReadyToCombine() || !newData.isReadyToCombine()) {
      return null;
    }

    Document document = newData.getDocument();
    if (document == null || document != oldData.getDocument()) {
      return null;
    }

    Object newDataText = newData.getTransferData(DataFlavor.stringFlavor);
    Object oldDataText = oldData.getTransferData(DataFlavor.stringFlavor);
    if (newDataText == null || oldDataText == null) {
      return null;
    }

    if (oldData.isCut()) {
      if (newData.getStartOffset() == oldData.getStartOffset()) {
        return new KillRingTransferable(oldDataText.toString() + newDataText, document, oldData.getStartOffset(), newData.getEndOffset(), newData.isCut());
      }
    }

    if (newData.getStartOffset() == oldData.getEndOffset()) {
      return new KillRingTransferable(oldDataText.toString() + newDataText, document, oldData.getStartOffset(), newData.getEndOffset(), false);
    }

    if (newData.getEndOffset() == oldData.getStartOffset()) {
      return new KillRingTransferable(newDataText.toString() + oldDataText, document, newData.getStartOffset(), oldData.getEndOffset(), false);
    }

    return null;
  }

  private static String getStringContent(Transferable content) {
    try {
      return (String)content.getTransferData(DataFlavor.stringFlavor);
    }
    catch (UnsupportedFlavorException | IOException ignore) {
    }
    return null;
  }

  private void deleteAfterAllowedMaximum() {
    int max = UISettings.getInstance().MAX_CLIPBOARD_CONTENTS;
    for (int i = myData.size() - 1; i >= max; i--) {
      myData.remove(i);
    }
  }

  @Override
  public Transferable getContents() {
    return myClipboardSynchronizer.getContents();
  }

  @Nullable
  @Override
  public <T> T getContents(@Nonnull DataFlavor flavor) {
    if (areDataFlavorsAvailable(flavor)) {
      //noinspection unchecked
      return (T)myClipboardSynchronizer.getData(flavor);
    }
    return null;
  }

  @Nonnull
  @Override
  public Transferable[] getAllContents() {
    String clipString = getContents(DataFlavor.stringFlavor);
    if (clipString != null && (myData.isEmpty() || !Comparing.equal(clipString, getStringContent(myData.get(0))))) {
      addToTheTopOfTheStack(new StringSelection(clipString));
    }
    return myData.toArray(new Transferable[0]);
  }

  public void removeContent(Transferable t) {
    Transferable current = myData.isEmpty() ? null : myData.get(0);
    myData.remove(t);
    if (Comparing.equal(t, current)) {
      Transferable newContent = !myData.isEmpty() ? myData.get(0) : new StringSelection("");
      setSystemClipboardContent(newContent);
      fireContentChanged(current, newContent);
    }
  }

  public void moveContentToStackTop(Transferable t) {
    Transferable current = myData.isEmpty() ? null : myData.get(0);
    if (!Comparing.equal(t, current)) {
      myData.remove(t);
      myData.add(0, t);
      setSystemClipboardContent(t);
      fireContentChanged(current, t);
    }
    else {
      setSystemClipboardContent(t);
    }
  }
}