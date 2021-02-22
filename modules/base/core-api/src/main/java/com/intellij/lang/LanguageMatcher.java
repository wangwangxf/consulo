// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import org.jetbrains.annotations.Contract;
import javax.annotation.Nonnull;

public abstract class LanguageMatcher {

  LanguageMatcher() {
  }

  public abstract boolean matchesLanguage(@Nonnull Language language);

  /**
   * Given the filter language X returns the matcher which matches language L if one the following is {@code true}:
   * <ul>
   * <li>X is not a metalanguage and X is L</li>
   * <li>X is a {@linkplain MetaLanguage#matchesLanguage metalanguage of L}</li>
   * </ul>
   */
  @Contract(pure = true)
  @Nonnull
  public static LanguageMatcher match(@Nonnull Language language) {
    if (language instanceof MetaLanguage) {
      return new MetaLanguageMatcher((MetaLanguage)language);
    }
    else {
      return new ExactMatcher(language);
    }
  }

  /**
   * Given the filter language X returns the matcher which matches language L if one the following is {@code true}:
   * <ul>
   * <li>X is not a metalanguage and X is a {@linkplain Language#getBaseLanguage base language of L}: {@code L.isKindOf(X) == true}</li>
   * <li>X is a {@linkplain MetaLanguage#matchesLanguage metalanguage of L} or one of its base languages</li>
   * </ul>
   */
  @Contract(pure = true)
  @Nonnull
  public static LanguageMatcher matchWithDialects(@Nonnull Language language) {
    if (language instanceof MetaLanguage) {
      return new MetaLanguageKindMatcher((MetaLanguage)language);
    }
    else {
      return new LanguageKindMatcher(language);
    }
  }
}

final class ExactMatcher extends LanguageMatcher {

  private final
  @Nonnull
  Language myLanguage;

  ExactMatcher(@Nonnull Language language) {
    myLanguage = language;
  }

  @Override
  public boolean matchesLanguage(@Nonnull Language language) {
    return myLanguage.is(language);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExactMatcher matcher = (ExactMatcher)o;

    if (!myLanguage.equals(matcher.myLanguage)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myLanguage.hashCode();
  }

  @Override
  public String toString() {
    return myLanguage.toString();
  }
}

final class LanguageKindMatcher extends LanguageMatcher {

  private final
  @Nonnull
  Language myLanguage;

  LanguageKindMatcher(@Nonnull Language language) {
    myLanguage = language;
  }

  @Override
  public boolean matchesLanguage(@Nonnull Language language) {
    return language.isKindOf(myLanguage);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LanguageKindMatcher matcher = (LanguageKindMatcher)o;

    if (!myLanguage.equals(matcher.myLanguage)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myLanguage.hashCode();
  }

  @Override
  public String toString() {
    return myLanguage + " with dialects";
  }
}

final class MetaLanguageMatcher extends LanguageMatcher {

  private final
  @Nonnull
  MetaLanguage myLanguage;

  MetaLanguageMatcher(@Nonnull MetaLanguage language) {
    myLanguage = language;
  }

  @Override
  public boolean matchesLanguage(@Nonnull Language language) {
    return myLanguage.matchesLanguage(language);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MetaLanguageMatcher matcher = (MetaLanguageMatcher)o;

    if (!myLanguage.equals(matcher.myLanguage)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myLanguage.hashCode();
  }

  @Override
  public String toString() {
    return myLanguage + " (meta)";
  }
}

final class MetaLanguageKindMatcher extends LanguageMatcher {

  @Nonnull
  private final MetaLanguage myLanguage;

  MetaLanguageKindMatcher(@Nonnull MetaLanguage language) {
    myLanguage = language;
  }

  @Override
  public boolean matchesLanguage(@Nonnull Language language) {
    return LanguageUtil.hierarchy(language).filter(myLanguage::matchesLanguage).isNotEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MetaLanguageKindMatcher matcher = (MetaLanguageKindMatcher)o;

    if (!myLanguage.equals(matcher.myLanguage)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myLanguage.hashCode();
  }

  @Override
  public String toString() {
    return myLanguage + " (meta) with dialects";
  }
}
