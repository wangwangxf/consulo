/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.tasks.context;

import consulo.component.extension.ExtensionPointName;
import com.intellij.util.xmlb.InvalidDataException;
import com.intellij.util.xmlb.WriteExternalException;
import com.intellij.util.xmlb.JDOMExternalizable;
import org.jdom.Element;
import javax.annotation.Nonnull;


/**
 * @author Dmitry Avdeev
 */
public abstract class WorkingContextProvider {

  public static final ExtensionPointName<WorkingContextProvider> EP_NAME = ExtensionPointName.create("consulo.tasks.contextProvider");

  /**
   * Short unique name.
   * Should be valid as a tag name (for serialization purposes).
   * No spaces, dots etc allowed.
   *
   * @return provider's name
   */
  @Nonnull
  public abstract String getId();

  /**
   * Short description (for UI)
   * @return
   */
  @Nonnull
  public abstract String getDescription();

  /**
   * Saves a component's state.
   * May delegate to {@link JDOMExternalizable#writeExternal(org.jdom.Element)}
   * @param toElement
   */
  public abstract void saveContext(Element toElement) throws WriteExternalException;

  public abstract void loadContext(Element fromElement) throws InvalidDataException;

  public abstract void clearContext();
}
