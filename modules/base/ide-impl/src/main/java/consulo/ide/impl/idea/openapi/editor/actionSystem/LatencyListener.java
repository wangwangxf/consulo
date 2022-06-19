// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.openapi.editor.actionSystem;

import consulo.codeEditor.Editor;
import consulo.component.messagebus.TopicImpl;
import consulo.component.messagebus.MessageBus;

/**
 * Reports typing latency measurements on the application-level {@link MessageBus}.
 */
public interface LatencyListener {
  TopicImpl<LatencyListener> TOPIC = new TopicImpl<>("Typing latency notifications", LatencyListener.class);

  /**
   * Record latency for a single key typed.
   */
  void recordTypingLatency(Editor editor, String action, long latencyMs);
}
