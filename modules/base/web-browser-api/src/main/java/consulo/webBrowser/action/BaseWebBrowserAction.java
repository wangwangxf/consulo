/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package consulo.webBrowser.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.webBrowser.WebBrowser;
import consulo.webBrowser.WebBrowserManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class BaseWebBrowserAction extends BaseOpenInBrowserAction {
  private final WebBrowser browser;

  public BaseWebBrowserAction(@Nonnull WebBrowser browser) {
    super(browser);

    this.browser = browser;
  }

  @Nullable
  @Override
  protected WebBrowser getBrowser(@Nonnull AnActionEvent event) {
    return WebBrowserManager.getInstance().isActive(browser) && browser.getPath() != null ? browser : null;
  }
}