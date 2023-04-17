/*
 * Copyright 2013-2023 consulo.io
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
package consulo.ide.impl.idea.compiler.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.application.progress.Task;
import consulo.compiler.CompilerBundle;
import consulo.ide.impl.compiler.TranslatingCompilerFilesMonitor;
import consulo.project.Project;
import consulo.project.startup.PostStartupActivity;
import consulo.ui.UIAccess;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 17/04/2023
 */
@ExtensionImpl
public class TranslationCompilerPostStartupActivity implements PostStartupActivity, DumbAware {
  @Override
  public void runActivity(@Nonnull Project project, @Nonnull UIAccess uiAccess) {
    TranslatingCompilerFilesMonitorImpl monitor = (TranslatingCompilerFilesMonitorImpl)TranslatingCompilerFilesMonitor.getInstance();

    monitor.startAsyncScan(project);

    Task.Backgroundable.queue(project, CompilerBundle.message("compiler.initial.scanning.progress.text"), false, indicator -> {
      monitor.runScan(indicator, project);
    });
  }
}
