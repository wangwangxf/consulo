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

package com.intellij.refactoring.rename.inplace;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import consulo.dataContext.DataContext;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.logging.Logger;
import consulo.editor.Editor;
import consulo.editor.ScrollType;
import consulo.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class VariableInplaceRenameHandler implements RenameHandler {
  private static final ThreadLocal<String> ourPreventInlineRenameFlag = new ThreadLocal<String>();
  private static final Logger LOG = Logger.getInstance(VariableInplaceRenameHandler.class);

  @Override
  public final boolean isAvailableOnDataContext(final DataContext dataContext) {
    final PsiElement element = PsiElementRenameHandler.getElement(dataContext);
    final Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    final PsiFile file = dataContext.getData(CommonDataKeys.PSI_FILE);
    if (editor == null || file == null) return false;

    if (ourPreventInlineRenameFlag.get() != null) {
      return false;
    }
    return isAvailable(element, editor, file);
  }

  protected boolean isAvailable(PsiElement element, Editor editor, PsiFile file) {
    final PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());

    RefactoringSupportProvider supportProvider =
            element == null ? null : LanguageRefactoringSupport.INSTANCE.forLanguage(element.getLanguage());
    return supportProvider != null &&
           editor.getSettings().isVariableInplaceRenameEnabled() &&
           supportProvider.isInplaceRenameAvailable(element, nameSuggestionContext);
  }

  @Override
  public final boolean isRenaming(final DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    PsiElement element = PsiElementRenameHandler.getElement(dataContext);
    if (element == null) {
      if (LookupManager.getActiveLookup(editor) != null) {
        final PsiElement elementUnderCaret = file.findElementAt(editor.getCaretModel().getOffset());
        if (elementUnderCaret != null) {
          final PsiElement parent = elementUnderCaret.getParent();
          if (parent instanceof PsiReference) {
            element = ((PsiReference)parent).resolve();
          } else {
            element = PsiTreeUtil.getParentOfType(elementUnderCaret, PsiNamedElement.class);
          }
        }
        if (element == null) return;
      } else {
        return;
      }
    }
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (checkAvailable(element, editor, dataContext)) {
      doRename(element, editor, dataContext);
    }
  }

  @Override
  public void invoke(@Nonnull final Project project, @Nonnull final PsiElement[] elements, final DataContext dataContext) {
    PsiElement element = elements.length == 1 ? elements[0] : null;
    if (element == null) element = PsiElementRenameHandler.getElement(dataContext);
    LOG.assertTrue(element != null);
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    if (checkAvailable(element, editor, dataContext)) {
      doRename(element, editor, dataContext);
    }
  }

  protected boolean checkAvailable(final PsiElement elementToRename, final Editor editor, final DataContext dataContext) {
    if (!isAvailableOnDataContext(dataContext)) {
      LOG.error("Recursive invocation");
      RenameHandlerRegistry.getInstance().getRenameHandler(dataContext).invoke(
              elementToRename.getProject(),
              editor,
              elementToRename.getContainingFile(), dataContext
      );
      return false;
    }
    return true;
  }

  @Nullable
  public InplaceRefactoring doRename(final @Nonnull PsiElement elementToRename, final Editor editor, final DataContext dataContext) {
    VariableInplaceRenamer renamer = createRenamer(elementToRename, editor);
    boolean startedRename = renamer == null ? false : renamer.performInplaceRename();

    if (!startedRename) {
      performDialogRename(elementToRename, editor, dataContext, renamer != null ? renamer.myInitialName : null);
    }
    return renamer;
  }

  protected static void performDialogRename(PsiElement elementToRename, Editor editor, DataContext dataContext) {
    performDialogRename(elementToRename, editor, dataContext, null);
  }

  protected static void performDialogRename(PsiElement elementToRename, Editor editor, DataContext dataContext, String initialName) {
    try {
      ourPreventInlineRenameFlag.set(initialName == null ? "" : initialName);
      RenameHandler handler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext);
      assert handler != null : elementToRename;
      handler.invoke(
              elementToRename.getProject(),
              editor,
              elementToRename.getContainingFile(), dataContext
      );
    } finally {
      ourPreventInlineRenameFlag.set(null);
    }
  }

  @Nullable
  public static String getInitialName() {
    final String str = ourPreventInlineRenameFlag.get();
    return StringUtil.isEmpty(str) ? null : str;
  }

  @Nullable
  protected VariableInplaceRenamer createRenamer(@Nonnull PsiElement elementToRename, Editor editor) {
    return new VariableInplaceRenamer((PsiNamedElement)elementToRename, editor);
  }
}
