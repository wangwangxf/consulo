/*
 * Copyright 2013-2023 consulo.io
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
package consulo.ide.impl.idea.codeInsight.lookup.impl;

import consulo.application.util.registry.Registry;
import consulo.codeEditor.Editor;
import consulo.ide.impl.idea.openapi.editor.ex.util.EditorUtil;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 26/06/2023
 */
public class LookupIconUtil {
  public static Image augmentIcon(@Nullable Editor editor, @Nullable Image icon, @Nonnull Image standard) {
    if (Registry.is("editor.scale.completion.icons")) {
      standard = EditorUtil.scaleIconAccordingEditorFont(standard, editor);

      if (icon != null) {
        icon = EditorUtil.scaleIconAccordingEditorFont(icon, editor);
      }
    }

    if (icon == null) {
      return standard;
    }

    if (icon.getWidth() == standard.getWidth() && icon.getHeight() == standard.getHeight()) {
      return icon;
    }

    return ImageEffects.layered(standard, icon);
  }
}
