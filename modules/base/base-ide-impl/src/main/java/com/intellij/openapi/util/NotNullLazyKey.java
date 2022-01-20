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
package com.intellij.openapi.util;

import com.intellij.util.NotNullFunction;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolder;

import javax.annotation.Nonnull;

/**
 * @author peter
 */
public class NotNullLazyKey<T, H extends UserDataHolder> extends Key<T> {
  private final NotNullFunction<H, T> myFunction;

  private NotNullLazyKey(@Nonnull String name, @Nonnull NotNullFunction<H, T> function) {
    super(name);
    myFunction = function;
  }

  @Nonnull
  public final T getValue(@Nonnull H h) {
    T data = h.getUserData(this);
    if (data == null) {
      h.putUserData(this, data = myFunction.fun(h));
    }
    return data;
  }

  public static <T, H extends UserDataHolder> NotNullLazyKey<T, H> create(@Nonnull String name, @Nonnull NotNullFunction<H, T> function) {
    return new NotNullLazyKey<>(name, function);
  }
}
