/*
 * Copyright 2013-2018 consulo.io
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
package consulo.options;

import consulo.application.Application;
import consulo.ide.impl.idea.openapi.options.ConfigurableEP;
import consulo.configurable.UnnamedConfigurable;
import consulo.util.xml.serializer.annotation.AbstractCollection;
import consulo.util.xml.serializer.annotation.Property;
import consulo.util.xml.serializer.annotation.Tag;

import jakarta.inject.Inject;

/**
 * @author VISTALL
 * @since 2018-08-24
 */
@Tag("configurable")
public class ApplicationConfigurableEP<T extends UnnamedConfigurable> extends ConfigurableEP<T> {
  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public ApplicationConfigurableEP[] children;

  @Override
  public ApplicationConfigurableEP[] getChildren() {
    if (children == null) {
      return null;
    }

    for (ApplicationConfigurableEP child : children) {
      child.myContainerOwner = myContainerOwner;
      child.myPluginDescriptor = myPluginDescriptor;
    }
    return children;
  }

  // used for children serialization
  private ApplicationConfigurableEP() {
    super(null);
  }

  @Inject
  public ApplicationConfigurableEP(Application application) {
    super(application);
  }
}
