/*
 * Copyright 2013-2022 consulo.io
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
package consulo.project.impl;

import consulo.project.Project;

import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 19/01/2022
 */
public class SingleProjectHolder {
  /**
   * @return the only open project if there is one, null if no or several projects are open
   */
  @Nullable
  public static Project theOnlyOpenProject() {
    return theProject;
  }

  public static volatile Project theProject;
}
