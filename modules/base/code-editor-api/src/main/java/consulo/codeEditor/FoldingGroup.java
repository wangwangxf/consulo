/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package consulo.codeEditor;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link FoldRegion}s with same FoldingGroup instances expand and collapse together.
 *
 * @author peter
 */
public final class FoldingGroup {
  private static final AtomicLong ourCounter = new AtomicLong();

  private final String myDebugName;
  private final long myId;

  private FoldingGroup(String debugName) {
    myDebugName = debugName;
    myId = ourCounter.incrementAndGet();
  }

  public static FoldingGroup newGroup(@Nonnull String debugName) {
    return new FoldingGroup(debugName);
  }

  public long getId() {
    return myId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FoldingGroup group = (FoldingGroup)o;

    if (myId != group.myId) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return (int)(myId ^ (myId >>> 32));
  }

  @Override
  public String toString() {
    return myDebugName;
  }
}
