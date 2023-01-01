/*
 * Copyright 2013-2021 consulo.io
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
package consulo.component.internal;

import consulo.component.ComponentManager;

import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 28/12/2021
 *
 * This is holder of Application instance for ExtensionPointName (deprecated methods without ComponentManager)
 */
public class RootComponentHolder {
  private static ComponentManager ourRootComponent;

  public static void setRootComponent(@Nullable ComponentManager rootComponent) {
    ourRootComponent = rootComponent;
  }

  public static ComponentManager getRootComponent() {
    return ourRootComponent;
  }
}
