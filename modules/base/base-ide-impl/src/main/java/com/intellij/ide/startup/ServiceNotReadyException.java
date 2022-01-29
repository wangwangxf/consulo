// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup;

import consulo.application.progress.ProcessCanceledException;

/**
 * If thrown during startup process, indicates incorrect service calling.
 */
public class ServiceNotReadyException extends ProcessCanceledException {
}
