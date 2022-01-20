/*
 * Copyright 2013-2016 consulo.io
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
package consulo.compiler.impl;

import consulo.component.extension.ExtensionPointName;
import com.intellij.openapi.module.Module;
import consulo.project.Project;
import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 20:22/12.06.13
 */
public interface AdditionalOutputDirectoriesProvider {
  ExtensionPointName<AdditionalOutputDirectoriesProvider> EP_NAME = ExtensionPointName.create("consulo.compiler.additionalOutputDirectoriesProvider");

  @Nonnull
  String[] getOutputDirectories(@Nonnull Project project, @Nonnull Module modules);
}
