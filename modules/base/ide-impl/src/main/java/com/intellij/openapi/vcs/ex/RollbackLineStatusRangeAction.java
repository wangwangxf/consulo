/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.ex;

import consulo.ui.ex.action.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import consulo.editor.Editor;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class RollbackLineStatusRangeAction extends RollbackLineStatusAction {
  @Nonnull
  private final LineStatusTracker myTracker;
  @Nullable private final Editor myEditor;
  @Nonnull
  private final Range myRange;

  public RollbackLineStatusRangeAction(@Nonnull LineStatusTracker tracker, @Nonnull Range range, @javax.annotation.Nullable Editor editor) {
    ActionUtil.copyFrom(this, IdeActions.SELECTED_CHANGES_ROLLBACK);

    myTracker = tracker;
    myEditor = editor;
    myRange = range;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(true);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    rollback(myTracker, myEditor, myRange);
  }
}
