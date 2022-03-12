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
package com.intellij.ide.projectView.impl.nodes;

import consulo.application.AllIcons;
import consulo.ui.ex.awt.tree.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import consulo.module.Module;
import consulo.project.Project;
import consulo.module.content.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.vfs.VfsUtilCore;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.pom.NavigatableWithText;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import javax.annotation.Nonnull;

import java.util.Arrays;
import java.util.Collection;

public abstract class AbstractModuleNode extends ProjectViewNode<Module> implements NavigatableWithText {
  protected AbstractModuleNode(Project project, Module module, ViewSettings viewSettings) {
    super(project, module, viewSettings);
  }

  @Override
  public void update(PresentationData presentation) {
    if (getValue().isDisposed()) {
      setValue(null);
      return;
    }
    presentation.setPresentableText(getValue().getName());
    if (showModuleNameInBold()) {
      presentation.addText(getValue().getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }

    presentation.setIcon(AllIcons.Nodes.Module);
  }

  protected boolean showModuleNameInBold() {
    return true;
  }

  @Override
  public String getTestPresentation() {
    return "Module";
  }

  @Override
  public Collection<VirtualFile> getRoots() {
    return Arrays.asList(ModuleRootManager.getInstance(getValue()).getContentRoots());
  }

  @Override
  public boolean contains(@Nonnull VirtualFile file) {
    Module module = getValue();
    if (module == null || module.isDisposed()) return false;

    final VirtualFile testee;
    if (file.getFileSystem() instanceof ArchiveFileSystem) {
      testee = ((ArchiveFileSystem)file.getFileSystem()).getLocalVirtualFileFor(file);
      if (testee == null) return false;
    }
    else {
      testee = file;
    }
    for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
      if (VfsUtilCore.isAncestor(root, testee, false)) return true;
    }
    return false;
  }

  @Override
  public String getToolTip() {
    return "tooltip";
  }

  @Override
  public void navigate(final boolean requestFocus) {
    Module module = getValue();
    if (module != null) {
      ProjectSettingsService.getInstance(myProject).openModuleSettings(module);
    }
  }

  @Override
  public String getNavigateActionText(boolean focusEditor) {
    return "Open Module Settings";
  }

  @Override
  public boolean canNavigate() {
    return ProjectSettingsService.getInstance(myProject).canOpenModuleSettings() && getValue() != null;
  }
}
