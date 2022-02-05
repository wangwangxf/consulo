// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

import consulo.editor.Editor;
import consulo.component.messagebus.Topic;
import consulo.component.messagebus.MessageBus;

/**
 * Reports typing latency measurements on the application-level {@link MessageBus}.
 */
public interface LatencyListener {
  Topic<LatencyListener> TOPIC = new Topic<>("Typing latency notifications", LatencyListener.class);

  /**
   * Record latency for a single key typed.
   */
  void recordTypingLatency(Editor editor, String action, long latencyMs);
}
