/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import consulo.xdebugger.breakpoints.XLineBreakpointResolverTypeExtension;
import javax.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class XToggleLineBreakpointActionHandler extends DebuggerActionHandler {

  private final boolean myTemporary;

  public XToggleLineBreakpointActionHandler(boolean temporary) {
    myTemporary = temporary;
  }

  @Override
  public boolean isEnabled(@Nonnull final Project project, final AnActionEvent event) {
    XLineBreakpointType<?>[] breakpointTypes = XDebuggerUtil.getInstance().getLineBreakpointTypes();
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    for (XSourcePosition position : XDebuggerUtilImpl.getAllCaretsPositions(project, event.getDataContext())) {
      for (XLineBreakpointType<?> breakpointType : breakpointTypes) {
        final VirtualFile file = position.getFile();
        final int line = position.getLine();
        if (XLineBreakpointResolverTypeExtension.INSTANCE.resolveBreakpointType(project, file, line) != null ||
            breakpointManager.findBreakpointAtLine(breakpointType, file, line) != null) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void perform(@Nonnull final Project project, final AnActionEvent event) {
    Editor editor = event.getData(CommonDataKeys.EDITOR);
    // do not toggle more than once on the same line
    Set<Integer> processedLines = new HashSet<>();
    for (XSourcePosition position : XDebuggerUtilImpl.getAllCaretsPositions(project, event.getDataContext())) {
      if (processedLines.add(position.getLine())) {
        XBreakpointUtil.toggleLineBreakpoint(project, position, editor, myTemporary, true);
      }
    }
  }
}
