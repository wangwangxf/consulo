/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.content.bundle;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.Service;
import consulo.application.Application;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

@Service(ComponentScope.APPLICATION)
public abstract class SdkTable implements BundleHolder {
  @Nonnull
  @Deprecated
  @DeprecationInfo("Use constructor injecting")
  public static SdkTable getInstance() {
    return Application.get().getInstance(SdkTable.class);
  }

  @Nullable
  public abstract Sdk findSdk(String name);

  @Nonnull
  public abstract Sdk[] getAllSdks();

  @Nonnull
  @Override
  public Sdk[] getBundles() {
    return getAllSdks();
  }

  public abstract List<Sdk> getSdksOfType(SdkTypeId type);

  @Nullable
  public Sdk findMostRecentSdkOfType(final SdkTypeId type) {
    return findMostRecentSdk(sdk -> sdk.getSdkType() == type);
  }

  @Nullable
  public Sdk findMostRecentSdk(@Nonnull Predicate<Sdk> condition) {
    Sdk found = null;
    for (Sdk each : getAllSdks()) {
      if (!condition.test(each)) continue;
      if (found == null) {
        found = each;
        continue;
      }
      if (compare(each.getVersionString(), found.getVersionString()) > 0) found = each;
    }
    return found;
  }

  private static int compare(@Nullable String o1, @Nullable String o2) {
    if (Objects.equals(o1, o2)) return 0;
    if (o1 == null) return -1;
    if (o2 == null) return 1;
    return o1.compareTo(o2);
  }

  @RequiredWriteAction
  public abstract void addSdk(@Nonnull Sdk sdk);

  @RequiredWriteAction
  public abstract void removeSdk(@Nonnull Sdk sdk);

  @RequiredWriteAction
  public abstract void updateSdk(@Nonnull Sdk originalSdk, @Nonnull Sdk modifiedSdk);

  public abstract SdkTypeId getDefaultSdkType();

  public abstract SdkTypeId getSdkTypeByName(String name);

  @Nullable
  public abstract Sdk findPredefinedSdkByType(@Nonnull SdkTypeId sdkType);

  @Nonnull
  public abstract Sdk createSdk(final String name, final SdkTypeId sdkType);
}
