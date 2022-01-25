/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.find.findUsages;

import consulo.application.ApplicationManager;
import com.intellij.openapi.util.NullableComputable;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceService;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageInfoFactory;
import consulo.application.util.function.Processor;
import javax.annotation.Nonnull;

import java.util.Collection;

public class FindUsagesHelper {
  public static boolean processUsagesInText(@Nonnull final PsiElement element,
                                               @Nonnull Collection<String> stringToSearch,
                                               @Nonnull GlobalSearchScope searchScope,
                                               @Nonnull Processor<UsageInfo> processor) {
    final TextRange elementTextRange = ApplicationManager.getApplication().runReadAction(new NullableComputable<TextRange>() {
      @Override
      public TextRange compute() {
        if (!element.isValid() || element instanceof PsiCompiledElement) return null;
        return element.getTextRange();
      }
    });
    UsageInfoFactory factory = new UsageInfoFactory() {
      @Override
      public UsageInfo createUsageInfo(@Nonnull PsiElement usage, int startOffset, int endOffset) {
        if (elementTextRange != null
            && usage.getContainingFile() == element.getContainingFile()
            && elementTextRange.contains(startOffset)
            && elementTextRange.contains(endOffset)) {
          return null;
        }

        PsiReference someReference = usage.findReferenceAt(startOffset);
        if (someReference != null) {
          PsiElement refElement = someReference.getElement();
          for (PsiReference ref : PsiReferenceService.getService().getReferences(refElement, new PsiReferenceService.Hints(element, null))) {
            if (element.getManager().areElementsEquivalent(ref.resolve(), element)) {
              TextRange range = ref.getRangeInElement().shiftRight(refElement.getTextRange().getStartOffset() - usage.getTextRange().getStartOffset());
              return new UsageInfo(usage, range.getStartOffset(), range.getEndOffset(), true);
            }
          }
        }

        return new UsageInfo(usage, startOffset, endOffset, true);
      }
    };
    for (String s : stringToSearch) {
      if (!PsiSearchHelperImpl.processTextOccurrences(element, s, searchScope, processor, factory)) return false;
    }
    return true;
  }
}
