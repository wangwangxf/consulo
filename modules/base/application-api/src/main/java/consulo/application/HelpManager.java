/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package consulo.application;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@ServiceAPI(ComponentScope.APPLICATION)
public interface HelpManager {
  /**
   * Returns help id (String)
   */
  Key<String> HELP_ID = Key.create("helpId");

  @Nonnull
  public static HelpManager getInstance() {
    return Application.get().getInstance(HelpManager.class);
  }

  void invokeHelp(@Nullable String id);
}
