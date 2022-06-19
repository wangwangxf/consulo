// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.codeInsight.completion;

import consulo.component.messagebus.TopicImpl;

/**
 * @author yole
 */
public interface CompletionPhaseListener {
  void completionPhaseChanged(boolean isCompletionRunning);

  TopicImpl<CompletionPhaseListener> TOPIC = new TopicImpl<>("CompletionPhaseListener", CompletionPhaseListener.class);
}
