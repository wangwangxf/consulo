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
package consulo.test.light.impl;

import consulo.component.macro.PathMacroFilter;
import consulo.application.macro.PathMacros;
import consulo.component.store.impl.internal.PathMacrosService;
import org.jdom.Element;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

/**
 * @author VISTALL
 * @since 2018-08-25
 */
public class LightPathMacrosService extends PathMacrosService {
  @Override
  public Set<String> getMacroNames(Element root, @Nullable PathMacroFilter filter, @Nonnull PathMacros pathMacros) {
    return Collections.emptySet();
  }
}
