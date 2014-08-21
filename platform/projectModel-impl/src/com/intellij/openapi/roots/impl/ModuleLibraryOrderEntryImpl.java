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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.PathUtil;
import org.consulo.lombok.annotations.Logger;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Library entry for module ("in-place") libraries
 *
 * @author dsl
 */
@Logger
public class ModuleLibraryOrderEntryImpl extends LibraryOrderEntryBaseImpl implements LibraryOrderEntry, ClonableOrderEntry, WritableOrderEntry {
  private final Library myLibrary;
  @NonNls public static final String ENTRY_TYPE = "module-library";
  private boolean myExported;
  @NonNls public static final String EXPORTED_ATTR = "exported";

  //cloning
  private ModuleLibraryOrderEntryImpl(Library library, ModuleRootLayerImpl rootLayer, boolean isExported, DependencyScope scope) {
    super(rootLayer, ProjectRootManagerImpl.getInstanceImpl(rootLayer.getProject()));
    myLibrary = ((LibraryEx)library).cloneLibrary(rootLayer);
    myExported = isExported;
    myScope = scope;
    Disposer.register(this, myLibrary);
    init();
  }

  ModuleLibraryOrderEntryImpl(String name, final PersistentLibraryKind kind, ModuleRootLayerImpl moduleRootLayer) {
    super(moduleRootLayer, ProjectRootManagerImpl.getInstanceImpl(moduleRootLayer.getProject()));
    myLibrary = LibraryTableImplUtil.createModuleLevelLibrary(name, kind, moduleRootLayer);
    Disposer.register(this, myLibrary);
    init();
  }

  ModuleLibraryOrderEntryImpl(Element element, ModuleRootLayerImpl moduleRootLayer) throws InvalidDataException {
    super(moduleRootLayer, ProjectRootManagerImpl.getInstanceImpl(moduleRootLayer.getProject()));
    LOGGER.assertTrue(ENTRY_TYPE.equals(element.getAttributeValue(OrderEntryFactory.ORDER_ENTRY_TYPE_ATTR)));
    myExported = element.getAttributeValue(EXPORTED_ATTR) != null;
    myScope = DependencyScope.readExternal(element);
    myLibrary = LibraryTableImplUtil.loadLibrary(element, moduleRootLayer);

    Disposer.register(this, myLibrary);
  }

  @Override
  protected RootProvider getRootProvider() {
    return myLibrary.getRootProvider();
  }

  @Override
  public Library getLibrary() {
    return myLibrary;
  }

  @Override
  public boolean isModuleLevel() {
    return true;
  }

  @Override
  public String getLibraryName() {
    return myLibrary.getName();
  }

  @Override
  public String getLibraryLevel() {
    return LibraryTableImplUtil.MODULE_LEVEL;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    final String name = myLibrary.getName();
    if (name != null) {
      return name;
    }
    else {
      if (myLibrary instanceof LibraryEx && ((LibraryEx)myLibrary).isDisposed()) {
        return "<unknown>";
      }

      final String[] urls = myLibrary.getUrls(OrderRootType.CLASSES);
      if (urls.length > 0) {
        String url = urls[0];
        return PathUtil.toPresentableUrl(url);
      }
      else {
        return ProjectBundle.message("library.empty.library.item");
      }
    }
  }

  @Override
  public boolean isValid() {
    return !isDisposed() && myLibrary != null;
  }

  @Override
  public <R> R accept(RootPolicy<R> policy, R initialValue) {
    return policy.visitLibraryOrderEntry(this, initialValue);
  }

  @Override
  public boolean isSynthetic() {
    return true;
  }

  @Override
  public OrderEntry cloneEntry(ModuleRootLayerImpl rootModel) {
    return new ModuleLibraryOrderEntryImpl(myLibrary, rootModel, myExported, myScope);
  }

  @Override
  public void writeExternal(Element rootElement)  {
    final Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    if (myExported) {
      element.setAttribute(EXPORTED_ATTR, "");
    }
    myScope.writeExternal(element);
    try {
      myLibrary.writeExternal(element);
      rootElement.addContent(element);
    }
    catch (WriteExternalException e) {
      LOGGER.error("Exception while writing module library: " + getLibraryName() + " in module: " + getOwnerModule().getName(), e);
    }
  }


  @Override
  public boolean isExported() {
    return myExported;
  }

  @Override
  public void setExported(boolean value) {
    myExported = value;
  }

  @Override
  @NotNull
  public DependencyScope getScope() {
    return myScope;
  }

  @Override
  public void setScope(@NotNull DependencyScope scope) {
    myScope = scope;
  }
}
