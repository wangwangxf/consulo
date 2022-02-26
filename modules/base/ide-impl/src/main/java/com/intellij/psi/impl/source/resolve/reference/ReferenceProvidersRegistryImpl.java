/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference;

import consulo.language.Language;
import consulo.language.LanguageExtension;
import consulo.language.psi.*;
import consulo.language.psi.ReferenceProvidersRegistry;
import consulo.application.dumb.IndexNotReadyException;
import com.intellij.openapi.util.Comparing;
import consulo.language.pattern.ElementPattern;
import com.intellij.psi.*;
import consulo.language.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;
import java.util.*;

@Singleton
public class ReferenceProvidersRegistryImpl extends ReferenceProvidersRegistry {
  private static final LanguageExtension<PsiReferenceContributor> CONTRIBUTOR_EXTENSION = new LanguageExtension<PsiReferenceContributor>(PsiReferenceContributor.EP_NAME.getName());
  private static final LanguageExtension<PsiReferenceProviderBean> REFERENCE_PROVIDER_EXTENSION = new LanguageExtension<PsiReferenceProviderBean>(PsiReferenceProviderBean.EP_NAME.getName());

  private static final Comparator<ProviderBinding.ProviderInfo<PsiReferenceProvider, ProcessingContext>> PRIORITY_COMPARATOR =
          new Comparator<ProviderBinding.ProviderInfo<PsiReferenceProvider, ProcessingContext>>() {
            @Override
            public int compare(ProviderBinding.ProviderInfo<PsiReferenceProvider, ProcessingContext> o1, ProviderBinding.ProviderInfo<PsiReferenceProvider, ProcessingContext> o2) {
              return Comparing.compare(o2.priority, o1.priority);
            }
          };

  private final Map<Language, PsiReferenceRegistrarImpl> myRegistrars = FactoryMap.create(language -> {
    PsiReferenceRegistrarImpl registrar = new PsiReferenceRegistrarImpl();
    for (PsiReferenceContributor contributor : CONTRIBUTOR_EXTENSION.allForLanguage(language)) {
      contributor.registerReferenceProviders(registrar);
    }

    List<PsiReferenceProviderBean> referenceProviderBeans = REFERENCE_PROVIDER_EXTENSION.allForLanguage(language);
    for (final PsiReferenceProviderBean providerBean : referenceProviderBeans) {
      final ElementPattern<PsiElement> pattern = providerBean.createElementPattern();
      if (pattern != null) {
        registrar.registerReferenceProvider(pattern, new PsiReferenceProvider() {

          PsiReferenceProvider myProvider;

          @Nonnull
          @Override
          public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull ProcessingContext context) {
            if (myProvider == null) {

              myProvider = providerBean.instantiate();
              if (myProvider == null) {
                myProvider = NULL_REFERENCE_PROVIDER;
              }
            }
            return myProvider.getReferencesByElement(element, context);
          }
        });
      }
    }
    return registrar;
  });

  @Override
  public synchronized PsiReferenceRegistrarImpl getRegistrar(Language language) {
    return myRegistrars.get(language);
  }

  @Override
  protected PsiReference[] doGetReferencesFromProviders(PsiElement context, PsiReferenceService.Hints hints) {
    List<ProviderBinding.ProviderInfo<PsiReferenceProvider, ProcessingContext>> providersForContextLanguage = getRegistrar(context.getLanguage()).getPairsByElement(context, hints);

    List<ProviderBinding.ProviderInfo<PsiReferenceProvider, ProcessingContext>> providersForAllLanguages = getRegistrar(Language.ANY).getPairsByElement(context, hints);

    int providersCount = providersForContextLanguage.size() + providersForAllLanguages.size();

    if (providersCount == 0) {
      return PsiReference.EMPTY_ARRAY;
    }

    if (providersCount == 1) {
      final ProviderBinding.ProviderInfo<PsiReferenceProvider, ProcessingContext> firstProvider = (providersForAllLanguages.isEmpty() ? providersForContextLanguage : providersForAllLanguages).get(0);
      return firstProvider.provider.getReferencesByElement(context, firstProvider.processingContext);
    }

    List<ProviderBinding.ProviderInfo<PsiReferenceProvider, ProcessingContext>> list = ContainerUtil.concat(providersForContextLanguage, providersForAllLanguages);
    @SuppressWarnings("unchecked") ProviderBinding.ProviderInfo<PsiReferenceProvider, ProcessingContext>[] providers = list.toArray(new ProviderBinding.ProviderInfo[list.size()]);

    Arrays.sort(providers, PRIORITY_COMPARATOR);

    List<PsiReference> result = new ArrayList<PsiReference>();
    final double maxPriority = providers[0].priority;
    next:
    for (ProviderBinding.ProviderInfo<PsiReferenceProvider, ProcessingContext> trinity : providers) {
      final PsiReference[] refs;
      try {
        refs = trinity.provider.getReferencesByElement(context, trinity.processingContext);
      }
      catch (IndexNotReadyException ex) {
        continue;
      }
      if (trinity.priority != maxPriority) {
        for (PsiReference ref : refs) {
          for (PsiReference reference : result) {
            if (ref != null && ReferenceRange.containsRangeInElement(reference, ref.getRangeInElement())) {
              continue next;
            }
          }
        }
      }
      for (PsiReference ref : refs) {
        if (ref != null) {
          result.add(ref);
        }
      }
    }
    return result.isEmpty() ? PsiReference.EMPTY_ARRAY : ContainerUtil.toArray(result, new PsiReference[result.size()]);
  }
}
