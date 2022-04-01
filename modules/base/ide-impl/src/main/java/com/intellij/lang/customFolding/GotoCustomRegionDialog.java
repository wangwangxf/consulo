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
package com.intellij.lang.customFolding;

import consulo.ide.IdeBundle;
import consulo.language.Language;
import com.intellij.lang.folding.*;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.JBScrollPane;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.annotation.access.RequiredReadAction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */
public class GotoCustomRegionDialog extends DialogWrapper {
  private JBList myRegionsList;
  private JPanel myContentPane;
  private JBScrollPane myScrollPane;
  private final Editor myEditor;
  private final Project myProject;

  @RequiredReadAction
  protected GotoCustomRegionDialog(@Nullable Project project, @Nonnull Editor editor) {
    super(project);
    myEditor = editor;
    myProject = project;
    Collection<FoldingDescriptor> descriptors = getCustomFoldingDescriptors();
    init();
    if (descriptors.size() == 0) {
      myScrollPane.setVisible(false);
      myContentPane.add(new JLabel(IdeBundle.message("goto.custom.region.message.unavailable")), BorderLayout.NORTH);
      setOKActionEnabled(false);
    }
    else {
      myRegionsList.setModel(new MyListModel(orderByPosition(descriptors)));
      myRegionsList.setSelectedIndex(0);
    }
    setTitle(IdeBundle.message("goto.custom.region.command"));
  }

  @RequiredUIAccess
  @Override
  public JComponent getPreferredFocusedComponent() {
    if (!myRegionsList.isEmpty()) {
      return myRegionsList;
    }
    return super.getPreferredFocusedComponent();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  @RequiredReadAction
  private Collection<FoldingDescriptor> getCustomFoldingDescriptors() {
    Set<FoldingDescriptor> foldingDescriptors = new HashSet<FoldingDescriptor>();
    final Document document = myEditor.getDocument();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    PsiFile file = documentManager != null ? documentManager.getPsiFile(document) : null;
    if (file != null) {
      final FileViewProvider viewProvider = file.getViewProvider();
      for (final Language language : viewProvider.getLanguages()) {
        final PsiFile psi = viewProvider.getPsi(language);
        final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(language);
        if (psi != null) {
          for (FoldingDescriptor descriptor : LanguageFolding.buildFoldingDescriptors(foldingBuilder, psi, document, false)) {
            CustomFoldingBuilder customFoldingBuilder = getCustomFoldingBuilder(foldingBuilder, descriptor);
            if (customFoldingBuilder != null) {
              if (customFoldingBuilder.isCustomRegionStart(descriptor.getElement())) {
                foldingDescriptors.add(descriptor);
              }
            }
          }
        }
      }
    }
    return foldingDescriptors;
  }

  private static Collection<FoldingDescriptor> orderByPosition(Collection<FoldingDescriptor> descriptors) {
    List<FoldingDescriptor> sorted = new ArrayList<FoldingDescriptor>(descriptors.size());
    sorted.addAll(descriptors);
    Collections.sort(sorted, (descriptor1, descriptor2) -> {
      int pos1 = descriptor1.getElement().getTextRange().getStartOffset();
      int pos2 = descriptor2.getElement().getTextRange().getStartOffset();
      return pos1 - pos2;
    });
    return sorted;
  }

  private void createUIComponents() {
    myRegionsList = new JBList();
    myScrollPane = new JBScrollPane(myRegionsList);
  }

  @Nullable
  private static CustomFoldingBuilder getCustomFoldingBuilder(FoldingBuilder builder, FoldingDescriptor descriptor) {
    if (builder instanceof CustomFoldingBuilder) return (CustomFoldingBuilder)builder;
    FoldingBuilder originalBuilder = descriptor.getElement().getUserData(CompositeFoldingBuilder.FOLDING_BUILDER);
    if (originalBuilder instanceof CustomFoldingBuilder) return (CustomFoldingBuilder)originalBuilder;
    return null;
  }


  private static class MyListModel extends DefaultListModel {
    private MyListModel(Collection<FoldingDescriptor> descriptors) {
      for (FoldingDescriptor descriptor : descriptors) {
        super.addElement(new MyFoldingDescriptorWrapper(descriptor));
      }
    }
  }

  private static class MyFoldingDescriptorWrapper {
    private final @Nonnull
    FoldingDescriptor myDescriptor;

    private MyFoldingDescriptorWrapper(@Nonnull FoldingDescriptor descriptor) {
      myDescriptor = descriptor;
    }

    @Nonnull
    public FoldingDescriptor getDescriptor() {
      return myDescriptor;
    }

    @Nullable
    @Override
    public String toString() {
      return myDescriptor.getPlaceholderText();
    }
  }

  @Nullable
  public PsiElement getNavigationElement() {
    Object selection = myRegionsList.getSelectedValue();
    if (selection instanceof MyFoldingDescriptorWrapper) {
      return  ((MyFoldingDescriptorWrapper)selection).getDescriptor().getElement().getPsi();
    }
    return null;
  }
}
