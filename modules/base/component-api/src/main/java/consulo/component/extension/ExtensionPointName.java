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

package consulo.component.extension;

import consulo.component.ComponentManager;
import consulo.annotation.DeprecationInfo;
import consulo.component.extension.internal.RootComponentHolder;
import consulo.container.plugin.PluginDescriptor;
import consulo.logging.Logger;
import consulo.component.util.PluginExceptionUtil;
import consulo.util.lang.ControlFlowException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author mike
 * <p>
 */
@Deprecated
@DeprecationInfo("Prefer StrictExtensionPointName")
public class ExtensionPointName<T> {
  private static final Logger LOG = Logger.getInstance(ExtensionPointName.class);

  private final ExtensionPointId<T> myId;

  @Deprecated
  @DeprecationInfo("Use #create()")
  public ExtensionPointName(@Nonnull String name) {
    myId = ExtensionPointId.of(name);
  }

  @SuppressWarnings("deprecation")
  public static <T> ExtensionPointName<T> create(@Nonnull String name) {
    return new ExtensionPointName<>(name);
  }

  public String getName() {
    return myId.toString();
  }

  @Nonnull
  public ExtensionPointId<T> getId() {
    return myId;
  }

  @Override
  public String toString() {
    return myId.toString();
  }

  @Nonnull
  @Deprecated
  public T[] getExtensions() {
    return getExtensions(RootComponentHolder.getRootComponent());
  }

  @Nonnull
  @Deprecated
  public T[] getExtensions(@Nonnull ComponentManager componentManager) {
    return componentManager.getExtensions(this);
  }

  public boolean hasAnyExtensions() {
    return hasAnyExtensions(RootComponentHolder.getRootComponent());
  }

  public boolean hasAnyExtensions(@Nonnull ComponentManager manager) {
    return manager.getExtensionPoint(this).hasAnyExtensions();
  }

  @Nonnull
  @Deprecated
  @DeprecationInfo("Use with component manager")
  public List<T> getExtensionList() {
    return getExtensionList(RootComponentHolder.getRootComponent());
  }

  @Nonnull
  public List<T> getExtensionList(@Nonnull ComponentManager componentManager) {
    return componentManager.getExtensionList(this);
  }

  @Nullable
  public <V extends T> V findExtension(@Nonnull Class<V> instanceOf) {
    return findExtension(RootComponentHolder.getRootComponent(), instanceOf);
  }

  @Nullable
  public <V extends T> V findExtension(@Nonnull ComponentManager componentManager, @Nonnull Class<V> instanceOf) {
    return componentManager.findExtension(this, instanceOf);
  }

  @Nonnull
  public <V extends T> V findExtensionOrFail(@Nonnull Class<V> instanceOf) {
    return findExtensionOrFail(RootComponentHolder.getRootComponent(), instanceOf);
  }

  @Nonnull
  public <V extends T> V findExtensionOrFail(@Nonnull ComponentManager componentManager, @Nonnull Class<V> instanceOf) {
    V extension = componentManager.findExtension(this, instanceOf);
    if (extension == null) {
      throw new IllegalArgumentException("Extension point: " + getName() + " not contains extension of type: " + instanceOf);
    }
    return extension;
  }

  public void forEachExtensionSafe(@Nonnull Consumer<T> consumer) {
    forEachExtensionSafe(RootComponentHolder.getRootComponent(), consumer);
  }

  public void forEachExtensionSafe(@Nonnull ComponentManager manager, @Nonnull Consumer<T> consumer) {
    processWithPluginDescriptor(manager, (value, pluginDescriptor) -> {
      try {
        consumer.accept(value);
      }
      catch (Throwable e) {
        if (e instanceof ControlFlowException) {
          throw ControlFlowException.rethrow(e);
        }
        PluginExceptionUtil.logPluginError(LOG, e.getMessage(), e, value.getClass());
      }
    });
  }

  @Nullable
  public <R> R computeSafeIfAny(@Nonnull Function<? super T, ? extends R> processor) {
    return computeSafeIfAny(RootComponentHolder.getRootComponent(), processor);
  }

  @Nullable
  public <R> R computeSafeIfAny(@Nonnull ComponentManager componentManager, @Nonnull Function<? super T, ? extends R> processor) {
    for (T extension : getExtensionList(componentManager)) {
      try {
        R result = processor.apply(extension);
        if (result != null) {
          return result;
        }
      }
      catch (Throwable e) {
        if (e instanceof ControlFlowException) {
          throw ControlFlowException.rethrow(e);
        }
        PluginExceptionUtil.logPluginError(LOG, e.getMessage(), e, extension.getClass());
      }
    }
    return null;
  }

  @Nullable
  public T findFirstSafe(@Nonnull ComponentManager componentManager, @Nonnull Predicate<T> predicate) {
    for (T extension : getExtensionList(componentManager)) {
      try {
        if (predicate.test(extension)) {
          return extension;
        }
      }
      catch (Throwable e) {
        if (e instanceof ControlFlowException) {
          throw ControlFlowException.rethrow(e);
        }
        PluginExceptionUtil.logPluginError(LOG, e.getMessage(), e, extension.getClass());
      }
    }
    return null;
  }

  @Nullable
  public T findFirstSafe(@Nonnull Predicate<T> predicate) {
    return findFirstSafe(RootComponentHolder.getRootComponent(), predicate);
  }

  public void processWithPluginDescriptor(@Nonnull ComponentManager manager, @Nonnull BiConsumer<? super T, ? super PluginDescriptor> consumer) {
    manager.getExtensionPoint(this).processWithPluginDescriptor(consumer);
  }

  public void processWithPluginDescriptor(@Nonnull BiConsumer<? super T, ? super PluginDescriptor> consumer) {
    processWithPluginDescriptor(RootComponentHolder.getRootComponent(), consumer);
  }
}
