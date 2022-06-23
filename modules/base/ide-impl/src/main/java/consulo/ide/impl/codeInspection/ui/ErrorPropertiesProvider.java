/*
 * Copyright 2013-2020 consulo.io
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
package consulo.ide.impl.codeInspection.ui;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.Extension;
import consulo.component.extension.ExtensionPointName;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.ui.Component;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 2020-04-25
 */
@Extension(ComponentScope.APPLICATION)
public interface ErrorPropertiesProvider {
  ExtensionPointName<ErrorPropertiesProvider> EP_NAME = ExtensionPointName.create(ErrorPropertiesProvider.class);

  void fillProperties(@Nonnull Consumer<Component> componentConsumer, @Nonnull SimpleConfigurableByProperties.PropertyBuilder builder);
}
