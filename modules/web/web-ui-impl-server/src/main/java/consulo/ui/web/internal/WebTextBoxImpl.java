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
package consulo.ui.web.internal;

import consulo.ui.RequiredUIAccess;
import consulo.ui.TextBox;
import consulo.ui.web.internal.base.UIComponentWithVaadinComponent;
import consulo.ui.web.internal.base.VaadinComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2019-02-18
 */
public class WebTextBoxImpl extends UIComponentWithVaadinComponent<WebTextBoxImpl.Vaadin> implements TextBox {
  @Nullable
  @Override
  public TextBox setPlaceholder(@Nullable String text) {
    return null;
  }

  @Nonnull
  @Override
  public TextBox setVisibleLength(int columns) {
    return null;
  }

  public static class Vaadin extends VaadinComponent {
  }

  public WebTextBoxImpl(String text) {
    setValue(text, false);
  }

  @Override
  @Nonnull
  public Vaadin createVaadinComponent() {
    return new Vaadin();
  }

  @Nullable
  @Override
  public String getValue() {
    return getVaadinComponent().getState().caption;
  }

  @RequiredUIAccess
  @Override
  public void setValue(String value, boolean fireEvents) {
    getVaadinComponent().getState().caption = value;
    getVaadinComponent().markAsDirty();
  }
}
