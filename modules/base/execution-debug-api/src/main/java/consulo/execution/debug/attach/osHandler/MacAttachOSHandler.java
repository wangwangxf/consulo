// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.execution.debug.attach.osHandler;

import consulo.execution.debug.attach.EnvironmentAwareHost;
import javax.annotation.Nonnull;

class MacAttachOSHandler extends UnixAttachOSHandler {
  MacAttachOSHandler(@Nonnull EnvironmentAwareHost host) {
    super(host, AttachOSHandler.OSType.MACOSX);
  }
}