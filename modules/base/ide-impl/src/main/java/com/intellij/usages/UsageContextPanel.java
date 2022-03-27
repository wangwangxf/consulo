/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.usages;

import consulo.component.extension.ExtensionPointName;
import consulo.usage.UsageInfo;
import consulo.disposer.Disposable;
import consulo.usage.UsageView;

import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import javax.swing.*;
import java.util.List;

/**
 * Component showing additional information for the selected usage in the Usage View.
 * Examples: Preview, Data flow, Call hierarchy
 */
public interface UsageContextPanel extends Disposable {
  /** usage selection changes, panel should update its view for the newly select usages
   *
   * @param infos  null means there are no usages to show
   */
  void updateLayout(@Nullable List<? extends UsageInfo> infos);

  @Nonnull
  JComponent createComponent();

  interface Provider {
    ExtensionPointName<Provider> EP_NAME = ExtensionPointName.create("consulo.usageContextPanelProvider");

    @Nonnull
    UsageContextPanel create(@Nonnull UsageView usageView);

    /**
     * E.g. Call hierarchy is not available for variable usages
     */
    boolean isAvailableFor(@Nonnull UsageView usageView);

    @Nonnull
    String getTabTitle();
  }
}
