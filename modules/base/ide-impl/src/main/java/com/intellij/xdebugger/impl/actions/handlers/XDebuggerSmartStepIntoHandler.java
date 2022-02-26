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
package com.intellij.xdebugger.impl.actions.handlers;

import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import consulo.fileEditor.TextEditor;
import consulo.ide.ui.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.ui.ex.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import consulo.debugger.XDebugSession;
import consulo.debugger.XSourcePosition;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import consulo.debugger.step.XSmartStepIntoHandler;
import consulo.debugger.step.XSmartStepIntoVariant;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author nik
 */
public class XDebuggerSmartStepIntoHandler extends XDebuggerSuspendedActionHandler {

  @Override
  protected boolean isEnabled(@Nonnull XDebugSession session, DataContext dataContext) {
    return super.isEnabled(session, dataContext) && session.getDebugProcess().getSmartStepIntoHandler() != null;
  }

  @Override
  protected void perform(@Nonnull XDebugSession session, DataContext dataContext) {
    XSmartStepIntoHandler<?> handler = session.getDebugProcess().getSmartStepIntoHandler();
    XSourcePosition position = session.getTopFramePosition();
    if (position == null || handler == null) return;

    FileEditor editor = FileEditorManager.getInstance(session.getProject()).getSelectedEditor(position.getFile());
    if (editor instanceof TextEditor) {
      doSmartStepInto(handler, position, session, ((TextEditor)editor).getEditor());
    }
  }

  private static <V extends XSmartStepIntoVariant> void doSmartStepInto(final XSmartStepIntoHandler<V> handler,
                                                                        XSourcePosition position,
                                                                        final XDebugSession session,
                                                                        Editor editor) {
    List<V> variants = handler.computeSmartStepVariants(position);
    if (variants.isEmpty()) {
      session.stepInto();
      return;
    }
    else if (variants.size() == 1) {
      session.smartStepInto(handler, variants.get(0));
      return;
    }

    ListPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<V>(handler.getPopupTitle(position), variants) {
      @Override
      public Image getIconFor(V aValue) {
        return aValue.getIcon();
      }

      @Nonnull
      @Override
      public String getTextFor(V value) {
        return value.getText();
      }

      @Override
      public PopupStep onChosen(V selectedValue, boolean finalChoice) {
        session.smartStepInto(handler, selectedValue);
        return FINAL_CHOICE;
      }
    });
    DebuggerUIUtil.showPopupForEditorLine(popup, editor, position.getLine());
  }
}
