/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.editor.textarea;

import consulo.editor.FoldRegion;
import consulo.editor.FoldingModel;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Denis Zhdanov
 * @since 11/2/11 6:13 PM
 */
public class TextComponentFoldingModel implements FoldingModel {

  @Override
  public FoldRegion addFoldRegion(int startOffset, int endOffset, @Nonnull String placeholderText) {
    return null;
  }

  @Override
  public boolean addFoldRegion(@Nonnull FoldRegion region) {
    return false;
  }

  @Override
  public void removeFoldRegion(@Nonnull FoldRegion region) {
  }

  @Nonnull
  @Override
  public FoldRegion[] getAllFoldRegions() {
    return FoldRegion.EMPTY_ARRAY;
  }

  @Override
  public boolean isOffsetCollapsed(int offset) {
    return false;
  }

  @Override
  public FoldRegion getCollapsedRegionAtOffset(int offset) {
    return null;
  }

  @Nullable
  @Override
  public FoldRegion getFoldRegion(int startOffset, int endOffset) {
    return null;
  }

  @Override
  public void runBatchFoldingOperation(@Nonnull Runnable operation) {
  }

  @Override
  public void runBatchFoldingOperation(@Nonnull Runnable operation, boolean moveCaretFromCollapsedRegion) {
  }

  @Override
  public void runBatchFoldingOperationDoNotCollapseCaret(@Nonnull Runnable operation) {
  }
}
