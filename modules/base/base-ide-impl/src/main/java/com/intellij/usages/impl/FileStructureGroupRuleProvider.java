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

package com.intellij.usages.impl;

import consulo.component.extension.ExtensionPointName;
import consulo.project.Project;
import com.intellij.usages.rules.UsageGroupingRule;

import javax.annotation.Nullable;

public interface FileStructureGroupRuleProvider {
  ExtensionPointName<FileStructureGroupRuleProvider> EP_NAME = ExtensionPointName.create("consulo.fileStructureGroupRuleProvider");

  @Nullable
  UsageGroupingRule getUsageGroupingRule(final Project project);
}
