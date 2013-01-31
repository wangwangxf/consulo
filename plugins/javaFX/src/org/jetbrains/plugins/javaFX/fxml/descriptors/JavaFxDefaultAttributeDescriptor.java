/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;

/**
 * User: anna
 * Date: 1/10/13
 */
public class JavaFxDefaultAttributeDescriptor extends JavaFxPropertyAttributeDescriptor {
  private static final Logger LOG = Logger.getInstance("#" + JavaFxDefaultAttributeDescriptor.class.getName());

  public JavaFxDefaultAttributeDescriptor(String name, PsiClass psiClass) {
    super(name, psiClass);
  }

  @Override
  public boolean hasIdType() {
    return getName().equals(FxmlConstants.FX_ID);
  }

  @Override
  public boolean isEnumerated() {
    return getName().equals("fx:constant");
  }

  @Override
  protected PsiClass getEnum() {
    return isEnumerated() ? getPsiClass() : null ;
  }

  protected boolean isConstant(PsiField field) {
    return field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL) && field.hasModifierProperty(PsiModifier.PUBLIC);
  }
}