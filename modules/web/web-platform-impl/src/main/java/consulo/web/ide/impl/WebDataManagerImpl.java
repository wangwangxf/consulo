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
package consulo.web.ide.impl;

import com.intellij.openapi.actionSystem.AsyncDataContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.wm.WindowManager;
import consulo.ide.base.BaseDataManager;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.awt.*;

/**
 * @author VISTALL
 * @since 2019-02-16
 */
@Singleton
public class WebDataManagerImpl extends BaseDataManager {
  @Inject
  public WebDataManagerImpl(Provider<WindowManager> windowManagerProvider) {
    super(windowManagerProvider);
  }

  @Nonnull
  @Override
  public DataContext getDataContext() {
    return new MyUIDataContext(this, null);
  }

  @Nonnull
  @Override
  public AsyncDataContext createAsyncDataContext(@Nonnull DataContext dataContext) {
    throw new UnsupportedOperationException();
  }

  @Override
  public DataContext getDataContextTest(Component component) {
    return getDataContext();
  }
}
