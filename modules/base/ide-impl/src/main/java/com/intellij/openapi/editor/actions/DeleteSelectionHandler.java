/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import consulo.dataContext.DataContext;
import consulo.undoRedo.CommandProcessor;
import consulo.codeEditor.Caret;
import consulo.codeEditor.CaretAction;
import consulo.codeEditor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import consulo.ui.ex.awt.CopyPasteManager;
import consulo.annotation.access.RequiredWriteAction;

import javax.annotation.Nullable;

public class DeleteSelectionHandler extends EditorWriteActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public DeleteSelectionHandler(EditorActionHandler handler) {
    myOriginalHandler = handler;
  }

  @RequiredWriteAction
  @Override
  public void executeWriteAction(Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (caret == null ? editor.getSelectionModel().hasSelection(true) : caret.hasSelection()) {
      EditorUIUtil.hideCursorInEditor(editor);
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      CopyPasteManager.getInstance().stopKillRings();
      CaretAction action = c -> EditorModificationUtil.deleteSelectedText(editor);
      if (caret == null) {
        editor.getCaretModel().runForEachCaret(action);
      }
      else {
        action.perform(caret);
      }
    }
    else {
      myOriginalHandler.execute(editor, caret, dataContext);
    }
  }
}
