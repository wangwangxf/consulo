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
package com.intellij.execution.testframework.ui;

import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.ui.ConsoleView;
import consulo.project.Project;
import consulo.language.psi.scope.GlobalSearchScope;

/**
 * @author Roman.Chernyatchik
 */
public class TestsConsoleBuilderImpl extends TextConsoleBuilderImpl {
  public TestsConsoleBuilderImpl(final Project project,
                                 final GlobalSearchScope scope,
                                 boolean isViewer,
                                 boolean usePredefinedMessageFilter) {
    super(project, scope);
    setViewer(isViewer);
    setUsePredefinedMessageFilter(usePredefinedMessageFilter);
  }

  @Override
  protected ConsoleView createConsole() {
    return new TestsConsoleViewImpl(getProject(), getScope(), isViewer(), isUsePredefinedMessageFilter());
  }
}
