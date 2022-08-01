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
package consulo.component.extension;

import consulo.util.xml.serializer.annotation.Tag;
import consulo.util.xml.serializer.annotation.Text;

/**
 * @author VISTALL
 * @since 0:46/07.11.13
 */
@Tag("add")
@Deprecated(forRemoval = true)
public class ElementEP {
  @Text
  public String myValue;
}
