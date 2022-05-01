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
package consulo.roots.orderEntry;

import consulo.ide.setting.ShowSettingsUtil;
import consulo.project.Project;
import consulo.module.impl.internal.layer.orderEntry.LibraryOrderEntryImpl;
import consulo.content.impl.internal.library.LibraryEx;
import consulo.content.library.Library;
import consulo.ide.impl.idea.openapi.roots.ui.CellAppearanceEx;
import consulo.ide.impl.idea.openapi.roots.ui.FileAppearanceService;
import consulo.ide.impl.idea.openapi.roots.ui.OrderEntryAppearanceService;
import consulo.ide.impl.idea.openapi.roots.ui.configuration.classpath.ClasspathTableItem;
import consulo.ide.impl.idea.openapi.roots.ui.configuration.classpath.LibraryClasspathTableItem;
import consulo.content.base.BinariesOrderRootType;
import consulo.ide.setting.module.LibrariesConfigurator;
import consulo.ide.setting.module.ModulesConfigurator;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 06-Jun-16
 */
public class LibraryOrderEntryTypeEditor implements OrderEntryTypeEditor<LibraryOrderEntryImpl> {
  @RequiredUIAccess
  @Override
  public void navigate(@Nonnull final LibraryOrderEntryImpl orderEntry) {
    Project project = orderEntry.getModuleRootLayer().getProject();
    ShowSettingsUtil.getInstance().showProjectStructureDialog(project, config -> config.select(orderEntry, true));
  }

  @Nonnull
  @Override
  public CellAppearanceEx getCellAppearance(@Nonnull LibraryOrderEntryImpl orderEntry) {
    if (!orderEntry.isValid()) { //library can be removed
      return FileAppearanceService.getInstance().forInvalidUrl(orderEntry.getPresentableName());
    }
    Library library = orderEntry.getLibrary();
    assert library != null : orderEntry;
    return OrderEntryAppearanceService.getInstance()
            .forLibrary(orderEntry.getModuleRootLayer().getProject(), library, !((LibraryEx)library).getInvalidRootUrls(BinariesOrderRootType.getInstance()).isEmpty());
  }

  @Nonnull
  @Override
  public ClasspathTableItem<LibraryOrderEntryImpl> createTableItem(@Nonnull LibraryOrderEntryImpl orderEntry,
                                                                   @Nonnull Project project,
                                                                   @Nonnull ModulesConfigurator modulesConfigurator,
                                                                   @Nonnull LibrariesConfigurator librariesConfigurator) {
    return new LibraryClasspathTableItem<>(orderEntry, project, modulesConfigurator, librariesConfigurator);
  }
}
