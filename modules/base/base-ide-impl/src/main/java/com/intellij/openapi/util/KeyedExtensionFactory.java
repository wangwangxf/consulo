/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import consulo.component.extension.ExtensionPointName;
import consulo.component.extension.KeyedFactoryEPBean;
import consulo.annotation.DeprecationInfo;
import consulo.injecting.InjectingContainerOwner;

import javax.annotation.Nonnull;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * @author yole
 */
public abstract class KeyedExtensionFactory<T, KeyT> {
  private final Class<T> myInterfaceClass;
  private final ExtensionPointName<KeyedFactoryEPBean> myEpName;
  private final InjectingContainerOwner myInjectingContainerOwner;

  public KeyedExtensionFactory(@Nonnull Class<T> interfaceClass, @Nonnull ExtensionPointName<KeyedFactoryEPBean> epName, @Nonnull InjectingContainerOwner owner) {
    myInterfaceClass = interfaceClass;
    myEpName = epName;
    myInjectingContainerOwner = owner;
  }

  @Nonnull
  @Deprecated
  @DeprecationInfo("Please don't use this method. It has not explicit logic about creating 'T' value")
  public T get() {
    InvocationHandler handler = new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final List<KeyedFactoryEPBean> epBeans = myEpName.getExtensionList();
        //noinspection unchecked
        KeyT keyArg = (KeyT)args[0];
        String key = getKey(keyArg);
        Object result = getByKey(epBeans, key, method, args);
        if (result == null) {
          result = getByKey(epBeans, null, method, args);
        }
        return result;
      }
    };
    //noinspection unchecked
    return (T)Proxy.newProxyInstance(myInterfaceClass.getClassLoader(), new Class<?>[]{myInterfaceClass}, handler);
  }

  @SuppressWarnings("unchecked")
  public T getByKey(@Nonnull KeyT key) {
    final List<KeyedFactoryEPBean> epBeans = myEpName.getExtensionList();
    for (KeyedFactoryEPBean epBean : epBeans) {
      if (Comparing.strEqual(getKey(key), epBean.key)) {
        try {
          if (epBean.implementationClass != null) {
            return (T)epBean.instantiate(epBean.implementationClass, myInjectingContainerOwner.getInjectingContainer());
          }
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private T getByKey(final List<KeyedFactoryEPBean> epBeans, final String key, final Method method, final Object[] args) {
    Object result = null;
    for (KeyedFactoryEPBean epBean : epBeans) {
      if (Comparing.strEqual(epBean.key, key, true)) {
        try {
          if (epBean.implementationClass != null) {
            result = epBean.instantiate(epBean.implementationClass, myInjectingContainerOwner.getInjectingContainer());
          }
          else {
            Object factory = epBean.instantiate(epBean.factoryClass, myInjectingContainerOwner.getInjectingContainer());
            result = method.invoke(factory, args);
          }
          if (result != null) {
            break;
          }
        }
        catch (InvocationTargetException e) {
          if (e.getCause() instanceof RuntimeException) {
            throw (RuntimeException)e.getCause();
          }
          throw new RuntimeException(e);
        }
        catch (RuntimeException e) {
          throw e;
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
    //noinspection ConstantConditions
    return (T)result;
  }

  public abstract String getKey(@Nonnull KeyT key);
}
