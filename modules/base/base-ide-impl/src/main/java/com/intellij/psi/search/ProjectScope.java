// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi.search;

import consulo.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import javax.annotation.Nonnull;

public class ProjectScope {
  private static final NotNullLazyKey<GlobalSearchScope, Project> ALL_SCOPE_KEY = NotNullLazyKey.create("ALL_SCOPE_KEY", project -> ProjectScopeBuilder.getInstance(project).buildAllScope());

  private static final NotNullLazyKey<GlobalSearchScope, Project> PROJECT_SCOPE_KEY = NotNullLazyKey.create("PROJECT_SCOPE_KEY", project -> ProjectScopeBuilder.getInstance(project).buildProjectScope());

  private static final NotNullLazyKey<GlobalSearchScope, Project> LIBRARIES_SCOPE_KEY = NotNullLazyKey.create("LIBRARIES_SCOPE_KEY", project -> ProjectScopeBuilder.getInstance(project).buildLibrariesScope());

  private static final NotNullLazyKey<GlobalSearchScope, Project> CONTENT_SCOPE_KEY = NotNullLazyKey.create("CONTENT_SCOPE_KEY", project -> ProjectScopeBuilder.getInstance(project).buildContentScope());

  private static final NotNullLazyKey<GlobalSearchScope, Project> EVERYTHING_SCOPE_KEY = NotNullLazyKey.create("EVERYTHING_SCOPE_KEY", project -> ProjectScopeBuilder.getInstance(project).buildEverythingScope());

  private ProjectScope() {
  }

  /**
   * @return Scope for all things inside the project: files in the project content plus files in libraries/libraries sources
   */
  @Nonnull
  public static GlobalSearchScope getAllScope(@Nonnull Project project) {
    return ALL_SCOPE_KEY.getValue(project);
  }

  @Nonnull
  public static GlobalSearchScope getProjectScope(@Nonnull Project project) {
    return PROJECT_SCOPE_KEY.getValue(project);
  }

  @Nonnull
  public static GlobalSearchScope getLibrariesScope(@Nonnull Project project) {
    return LIBRARIES_SCOPE_KEY.getValue(project);
  }

  @Nonnull
  public static GlobalSearchScope getContentScope(@Nonnull Project project) {
    return CONTENT_SCOPE_KEY.getValue(project);
  }

  /**
   * @return The biggest possible scope: every file on the planet belongs to this.
   */
  @Nonnull
  public static GlobalSearchScope getEverythingScope(@Nonnull Project project) {
    return EVERYTHING_SCOPE_KEY.getValue(project);
  }
}