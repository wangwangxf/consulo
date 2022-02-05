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
package com.intellij.openapi.editor.colors.impl;

import consulo.editor.colorScheme.EditorColorsManager;
import consulo.editor.colorScheme.TextAttributesKey;
import consulo.editor.markup.TextAttributes;

import jakarta.inject.Singleton;

/**
 * @author Dennis.Ushakov
 */
@Singleton
public class TextAttributeKeyDefaultsProviderImpl implements TextAttributesKey.TextAttributeKeyDefaultsProvider {
  @Override
  public TextAttributes getDefaultAttributes(TextAttributesKey key) {
    return ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).getDefaultAttributes(key);
  }
}
