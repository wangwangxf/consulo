/*
 * Copyright 2013 must-be.org
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
package com.intellij.packaging.impl.run;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;

/**
 * @author VISTALL
 * @since 15:09/14.06.13
 */
public class BuildArtifactsBeforeRunTask extends AbstractArtifactsBeforeRunTask<BuildArtifactsBeforeRunTask> {
  public BuildArtifactsBeforeRunTask(Project project) {
    super(project, BuildArtifactsBeforeRunTaskProvider.ID);
  }
}
