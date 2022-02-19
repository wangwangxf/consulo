/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm.runner;

import consulo.execution.executor.Executor;
import com.intellij.execution.Location;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunProfile;
import com.intellij.execution.filters.CompositeFilter;
import com.intellij.execution.filters.FileHyperlinkInfo;
import consulo.execution.ui.console.Filter;
import consulo.execution.ui.console.HyperlinkInfo;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.SMStacktraceParserEx;
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction;
import com.intellij.execution.testframework.sm.runner.history.actions.ImportTestsGroup;
import consulo.execution.ui.console.ConsoleView;
import com.intellij.ide.util.PropertiesComponent;
import consulo.ui.ex.action.AnAction;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptorImpl;
import consulo.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.navigation.Navigatable;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import com.intellij.util.config.Storage;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Roman Chernyatchik
 * Use {@link SMRunnerConsolePropertiesProvider} so importer {@link AbstractImportTestsAction.ImportRunProfile#ImportRunProfile(VirtualFile, Project)}
 * would be able to create properties by read configuration and test navigation, rerun failed tests etc. would work on imported results
 */
public class SMTRunnerConsoleProperties extends TestConsoleProperties implements SMStacktraceParserEx {
  private final RunProfile myConfiguration;
  @Nonnull
  private final String myTestFrameworkName;
  private final CompositeFilter myCustomFilter;
  private boolean myIdBasedTestTree = false;
  private boolean myPrintTestingStartedTime = true;

  /**
   * @param config
   * @param testFrameworkName Prefix for storage which keeps runner settings. E.g. "RubyTestUnit"
   * @param executor
   */
  public SMTRunnerConsoleProperties(@Nonnull RunConfiguration config, @Nonnull String testFrameworkName, @Nonnull Executor executor) {
    this(config.getProject(), config, testFrameworkName, executor);
  }

  public SMTRunnerConsoleProperties(@Nonnull Project project,
                                    @Nonnull RunProfile config,
                                    @Nonnull String testFrameworkName,
                                    @Nonnull Executor executor) {
    super(getStorage(testFrameworkName), project, executor);
    myConfiguration = config;
    myTestFrameworkName = testFrameworkName;
    myCustomFilter = new CompositeFilter(project);
  }

  @Nonnull
  private static Storage.PropertiesComponentStorage getStorage(String testFrameworkName) {
    return new Storage.PropertiesComponentStorage(testFrameworkName + "Support.", PropertiesComponent.getInstance());
  }

  @Override
  public RunProfile getConfiguration() {
    return myConfiguration;
  }

  @Nullable
  @Override
  protected AnAction createImportAction() {
    return new ImportTestsGroup(this);
  }

  public boolean isIdBasedTestTree() {
    return myIdBasedTestTree;
  }

  public void setIdBasedTestTree(boolean idBasedTestTree) {
    myIdBasedTestTree = idBasedTestTree;
  }

  public boolean isPrintTestingStartedTime() {
    return myPrintTestingStartedTime;
  }

  public void setPrintTestingStartedTime(boolean printTestingStartedTime) {
    myPrintTestingStartedTime = printTestingStartedTime;
  }

  @Nullable
  @Override
  public Navigatable getErrorNavigatable(@Nonnull Location<?> location, @Nonnull String stacktrace) {
    return getErrorNavigatable(location.getProject(), stacktrace);
  }

  @javax.annotation.Nullable
  @Override
  public Navigatable getErrorNavigatable(@Nonnull final Project project, final @Nonnull String stacktrace) {
    if (myCustomFilter.isEmpty()) {
      return null;
    }

    // iterate stacktrace lines find first navigatable line using
    // stacktrace filters
    final int stacktraceLength = stacktrace.length();
    final String[] lines = StringUtil.splitByLines(stacktrace);
    for (String line : lines) {
      Filter.Result result;
      try {
        result = myCustomFilter.applyFilter(line, stacktraceLength);
      }
      catch (Throwable t) {
        throw new RuntimeException("Error while applying " + myCustomFilter + " to '" + line + "'", t);
      }
      final HyperlinkInfo info = result != null ? result.getFirstHyperlinkInfo() : null;
      if (info != null) {

        // covers 99% use existing cases
        if (info instanceof FileHyperlinkInfo) {
          return ((FileHyperlinkInfo)info).getDescriptor();
        }

        // otherwise
        return new Navigatable() {
          @Override
          public void navigate(boolean requestFocus) {
            info.navigate(project);
          }

          @Override
          public boolean canNavigate() {
            return true;
          }

          @Override
          public boolean canNavigateToSource() {
            return true;
          }
        };
      }
    }
    return null;
  }

  public void addStackTraceFilter(final Filter filter) {
    myCustomFilter.addFilter(filter);
  }

  @javax.annotation.Nullable
  @Deprecated
  protected Navigatable findSuitableNavigatableForLine(@Nonnull Project project, @Nonnull VirtualFile file, int line) {
    // lets find first non-ws psi element

    final Document doc = FileDocumentManager.getInstance().getDocument(file);
    final PsiFile psi = doc == null ? null : PsiDocumentManager.getInstance(project).getPsiFile(doc);
    if (psi == null) {
      return null;
    }

    int offset = doc.getLineStartOffset(line);
    int endOffset = doc.getLineEndOffset(line);
    for (int i = offset + 1; i < endOffset; i++) {
      PsiElement el = psi.findElementAt(i);
      if (el != null && !(el instanceof PsiWhiteSpace)) {
        offset = el.getTextOffset();
        break;
      }
    }

    return new OpenFileDescriptorImpl(project, file, offset);
  }

  public boolean fixEmptySuite() {
    return false;
  }

  @javax.annotation.Nullable
  public SMTestLocator getTestLocator() {
    return null;
  }

  @javax.annotation.Nullable
  public TestProxyFilterProvider getFilterProvider() {
    return null;
  }

  @Nullable
  public AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
    return null;
  }

  @Nonnull
  public String getTestFrameworkName() {
    return myTestFrameworkName;
  }

  public boolean isUndefined() {
    return false;
  }
}
