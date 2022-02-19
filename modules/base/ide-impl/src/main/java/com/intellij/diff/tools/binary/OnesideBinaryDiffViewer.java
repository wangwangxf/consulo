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
package com.intellij.diff.tools.binary;

import com.intellij.diff.DiffContext;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.holders.BinaryEditorHolder;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.side.OnesideDiffViewer;
import consulo.logging.Logger;
import consulo.fileEditor.FileEditor;
import consulo.application.progress.ProgressIndicator;
import javax.annotation.Nonnull;

import javax.swing.*;

public class OnesideBinaryDiffViewer extends OnesideDiffViewer<BinaryEditorHolder> {
  public static final Logger LOG = Logger.getInstance(OnesideBinaryDiffViewer.class);

  public OnesideBinaryDiffViewer(@Nonnull DiffContext context, @Nonnull DiffRequest request) {
    super(context, (ContentDiffRequest)request, BinaryEditorHolder.BinaryEditorHolderFactory.INSTANCE);
  }

  //
  // Diff
  //

  @Override
  @Nonnull
  protected Runnable performRediff(@Nonnull final ProgressIndicator indicator) {
    JComponent notification = getSide().select(DiffNotifications.createRemovedContent(), DiffNotifications.createInsertedContent());
    return applyNotification(notification);
  }

  @Nonnull
  private Runnable applyNotification(@javax.annotation.Nullable final JComponent notification) {
    return new Runnable() {
      @Override
      public void run() {
        clearDiffPresentation();
        if (notification != null) myPanel.addNotification(notification);
      }
    };
  }

  private void clearDiffPresentation() {
    myPanel.resetNotifications();
  }

  //
  // Getters
  //

  @Nonnull
  FileEditor getEditor() {
    return getEditorHolder().getEditor();
  }

  //
  // Misc
  //

  public static boolean canShowRequest(@Nonnull DiffContext context, @Nonnull DiffRequest request) {
    return OnesideDiffViewer.canShowRequest(context, request, BinaryEditorHolder.BinaryEditorHolderFactory.INSTANCE);
  }
}
