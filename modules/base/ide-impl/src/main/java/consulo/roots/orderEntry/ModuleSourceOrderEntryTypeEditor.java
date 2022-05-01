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

import consulo.application.AllIcons;
import consulo.module.impl.internal.layer.orderEntry.ModuleSourceOrderEntryImpl;
import consulo.ide.impl.idea.openapi.roots.ui.CellAppearanceEx;
import consulo.ide.impl.idea.openapi.roots.ui.util.SimpleTextCellAppearance;
import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 06-Jun-16
 */
public class ModuleSourceOrderEntryTypeEditor implements OrderEntryTypeEditor<ModuleSourceOrderEntryImpl> {
  @Nonnull
  @Override
  public CellAppearanceEx getCellAppearance(@Nonnull ModuleSourceOrderEntryImpl orderEntry) {
    return SimpleTextCellAppearance.synthetic(orderEntry.getPresentableName(), AllIcons.Nodes.Module);
  }
}
