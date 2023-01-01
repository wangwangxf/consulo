/*
 * Copyright 2013-2022 consulo.io
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
package consulo.build.ui;

import consulo.navigation.Navigatable;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.ui.ex.tree.PresentableNodeDescriptor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * @author VISTALL
 * @since 01-Aug-22
 */
public abstract class ExecutionNode<T extends ExecutionNode<T>> extends PresentableNodeDescriptor<T> {
  public ExecutionNode(@Nullable NodeDescriptor parentDescriptor) {
    super(parentDescriptor);
  }

  @Nonnull
  public abstract List<Navigatable> getNavigatables();
}
