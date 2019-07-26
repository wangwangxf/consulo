/*
 * Copyright 2013-2019 consulo.io
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
package consulo.container.impl;

import consulo.container.plugin.PluginDescriptor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author VISTALL
 * @since 2019-07-25
 */
public class PluginHolderModificator {
  private static final AtomicReference<List<PluginDescriptor>> ourPlugins = new AtomicReference<List<PluginDescriptor>>();

  @Nonnull
  public static List<PluginDescriptor> getPlugins() {
    List<PluginDescriptor> descriptors = ourPlugins.get();

    if (descriptors == null) {
      throw new IllegalArgumentException("Not initialized");
    }

    return descriptors;
  }

  public static boolean isInitialized() {
    return ourPlugins.get() != null;
  }

  public static void initalize(@Nonnull List<? extends PluginDescriptor> pluginDescriptors) {
    // create new array for dropping leak to list from another classloader etc
    ourPlugins.set(Collections.unmodifiableList(new ArrayList<PluginDescriptor>(pluginDescriptors)));
  }
}
