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
package consulo.ide.impl.progress;

import consulo.annotation.component.ServiceImpl;
import consulo.ide.impl.idea.ui.mac.foundation.MacUtil;
import consulo.application.impl.internal.progress.ProgressActivityFactory;
import consulo.application.util.SystemInfo;
import jakarta.inject.Singleton;

import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 23-Mar-22
 */
@Singleton
@ServiceImpl
public class DesktopProgressActivityFactory implements ProgressActivityFactory {
  private boolean myShouldStartActivity = SystemInfo.isMac && Boolean.parseBoolean(System.getProperty("consulo.mac.prevent.app.nap", "true"));

  @Nullable
  @Override
  public Runnable createActivity() {
    if (myShouldStartActivity) {
      return MacUtil.wakeUpNeo(this);
    }
    return null;
  }
}
