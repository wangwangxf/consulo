/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package consulo.ide.impl.psi.util.proximity;

import consulo.ide.impl.idea.openapi.util.NullableLazyKey;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.ide.impl.psi.util.ProximityLocation;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.ide.impl.idea.util.NullableFunction;
import javax.annotation.Nonnull;

/**
 * NOTE: This class is only registered in platform-based IDEs. In IDEA, SamePackageWeigher is used instead.
 *
 * @author yole
 */
public class SameDirectoryWeigher extends ProximityWeigher {
  private static final NullableLazyKey<PsiDirectory, ProximityLocation>
    PLACE_DIRECTORY = NullableLazyKey.create("placeDirectory", new NullableFunction<ProximityLocation, PsiDirectory>() {
    @Override
    public PsiDirectory fun(ProximityLocation location) {
      return PsiTreeUtil.getParentOfType(location.getPosition(), PsiDirectory.class, false);
    }
  });

  @Override
  public Comparable weigh(@Nonnull final PsiElement element, @Nonnull final ProximityLocation location) {
    if (location.getPosition() == null){
      return null;
    }
    final PsiDirectory placeDirectory = PLACE_DIRECTORY.getValue(location);
    if (placeDirectory == null) {
      return false;
    }

    return placeDirectory.equals(PsiTreeUtil.getParentOfType(element, PsiDirectory.class, false));
  }
}
