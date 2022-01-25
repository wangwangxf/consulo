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

package com.intellij.analysis;

import consulo.virtualFileSystem.archive.ArchiveFileType;
import com.intellij.openapi.actionSystem.*;
import consulo.logging.Logger;
import consulo.document.FileDocumentManager;
import com.intellij.openapi.help.HelpManager;
import consulo.module.Module;
import consulo.language.util.ModuleUtilCore;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public abstract class BaseAnalysisAction extends AnAction {
  private final String myTitle;
  private final String myAnalysisNoon;
  private static final Logger LOG = Logger.getInstance(BaseAnalysisAction.class);

  protected BaseAnalysisAction(String title, String analysisNoon) {
    myTitle = title;
    myAnalysisNoon = analysisNoon;
  }

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();
    final Project project = event.getProject();
    final boolean dumbMode = project == null || DumbService.getInstance(project).isDumb();
    presentation.setEnabled(!dumbMode && getInspectionScope(dataContext) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Module module = e.getData(LangDataKeys.MODULE);
    if (project == null) {
      return;
    }
    AnalysisScope scope = getInspectionScope(dataContext);
    LOG.assertTrue(scope != null);
    final boolean rememberScope = e.getPlace().equals(ActionPlaces.MAIN_MENU);
    final AnalysisUIOptions uiOptions = AnalysisUIOptions.getInstance(project);
    PsiElement element = dataContext.getData(CommonDataKeys.PSI_ELEMENT);
    BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(AnalysisScopeBundle.message("specify.analysis.scope", myTitle),
                                                                AnalysisScopeBundle.message("analysis.scope.title", myAnalysisNoon),
                                                                project,
                                                                scope,
                                                                module != null ? ModuleUtilCore.getModuleNameInReadAction(module) : null,
                                                                rememberScope, AnalysisUIOptions.getInstance(project), element){
      @Override
      @Nullable
      protected JComponent getAdditionalActionSettings(final Project project) {
        return BaseAnalysisAction.this.getAdditionalActionSettings(project, this);
      }


      @Override
      protected void doHelpAction() {
        HelpManager.getInstance().invokeHelp(getHelpTopic());
      }

      @Nonnull
      @Override
      protected Action[] createActions() {
        return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
      }
    };
    dlg.show();
    if (!dlg.isOK()) {
      canceled();
      return;
    }
    final int oldScopeType = uiOptions.SCOPE_TYPE;
    scope = dlg.getScope(uiOptions, scope, project, module);
    if (!rememberScope){
      uiOptions.SCOPE_TYPE = oldScopeType;
    }
    uiOptions.ANALYZE_TEST_SOURCES = dlg.isInspectTestSources();
    FileDocumentManager.getInstance().saveAllDocuments();

    analyze(project, scope);
  }

  @NonNls
  protected String getHelpTopic() {
    return "reference.dialogs.analyzeDependencies.scope";
  }

  protected void canceled() {
  }

  protected abstract void analyze(@Nonnull Project project, @Nonnull AnalysisScope scope);

  @Nullable
  private AnalysisScope getInspectionScope(@Nonnull DataContext dataContext) {
    if (dataContext.getData(CommonDataKeys.PROJECT) == null) return null;

    AnalysisScope scope = getInspectionScopeImpl(dataContext);

    return scope != null && scope.getScopeType() != AnalysisScope.INVALID ? scope : null;
  }

  @Nullable
  private AnalysisScope getInspectionScopeImpl(@Nonnull DataContext dataContext) {
    //Possible scopes: file, directory, package, project, module.
    Project projectContext = dataContext.getData(PlatformDataKeys.PROJECT_CONTEXT);
    if (projectContext != null) {
      return new AnalysisScope(projectContext);
    }

    final AnalysisScope analysisScope = dataContext.getData(AnalysisScopeUtil.KEY);
    if (analysisScope != null) {
      return analysisScope;
    }

    final PsiFile psiFile = dataContext.getData(CommonDataKeys.PSI_FILE);
    if (psiFile != null && psiFile.getManager().isInProject(psiFile)) {
      final VirtualFile file = psiFile.getVirtualFile();
      if (file != null && file.isValid() && file.getFileType() instanceof ArchiveFileType && acceptNonProjectDirectories()) {
        final VirtualFile jarRoot = ArchiveVfsUtil.getArchiveRootForLocalFile(file);
        if (jarRoot != null) {
          PsiDirectory psiDirectory = psiFile.getManager().findDirectory(jarRoot);
          if (psiDirectory != null) {
            return new AnalysisScope(psiDirectory);
          }
        }
      }
      return new AnalysisScope(psiFile);
    }

    VirtualFile[] virtualFiles = dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (virtualFiles != null && project != null) { //analyze on selection
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      if (virtualFiles.length == 1) {
        PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFiles[0]);
        if (psiDirectory != null && (acceptNonProjectDirectories() || psiDirectory.getManager().isInProject(psiDirectory))) {
          return new AnalysisScope(psiDirectory);
        }
      }
      Set<VirtualFile> files = new HashSet<VirtualFile>();
      for (VirtualFile vFile : virtualFiles) {
        if (fileIndex.isInContent(vFile)) {
          files.add(vFile);
        }
      }
      return new AnalysisScope(project, files);
    }

    Module moduleContext = dataContext.getData(LangDataKeys.MODULE_CONTEXT);
    if (moduleContext != null) {
      return new AnalysisScope(moduleContext);
    }

    Module[] modulesArray = dataContext.getData(LangDataKeys.MODULE_CONTEXT_ARRAY);
    if (modulesArray != null) {
      return new AnalysisScope(modulesArray);
    }
    return project == null ? null : new AnalysisScope(project);
  }

  protected boolean acceptNonProjectDirectories() {
    return false;
  }

  @Nullable
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog){
    return null;
  }

}
