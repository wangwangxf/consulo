/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.actions;

import consulo.project.DumbAware;
import com.intellij.xdebugger.impl.DebuggerSupport;
import javax.annotation.Nonnull;

/**
 * @author nik
 */
public class ToggleTemporaryLineBreakpointAction extends XDebuggerActionBase implements DumbAware {
  public ToggleTemporaryLineBreakpointAction() {
    super(true);
  }

  @Nonnull
  protected DebuggerActionHandler getHandler(@Nonnull final DebuggerSupport debuggerSupport) {
    return debuggerSupport.getToggleTemporaryLineBreakpointHandler();
  }
}
