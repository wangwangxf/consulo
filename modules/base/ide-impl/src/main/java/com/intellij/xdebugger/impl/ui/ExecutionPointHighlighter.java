// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui;

import consulo.application.ApplicationManager;
import consulo.application.TransactionGuard;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.codeEditor.colorScheme.EditorColorsManager;
import consulo.codeEditor.colorScheme.EditorColorsScheme;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptorImpl;
import consulo.fileEditor.TextEditor;
import consulo.codeEditor.markup.*;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.document.util.TextRange;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.ui.AppUIUtil;
import consulo.document.impl.DocumentUtil;
import consulo.debugger.XSourcePosition;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import consulo.debugger.ui.DebuggerColors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author nik
 */
public class ExecutionPointHighlighter {
  private final Project myProject;
  private RangeHighlighter myRangeHighlighter;
  private Editor myEditor;
  private XSourcePosition mySourcePosition;
  private OpenFileDescriptorImpl myOpenFileDescriptor;
  private boolean myNotTopFrame;
  private GutterIconRenderer myGutterIconRenderer;
  public static final Key<Boolean> EXECUTION_POINT_HIGHLIGHTER_TOP_FRAME_KEY = Key.create("EXECUTION_POINT_HIGHLIGHTER_TOP_FRAME_KEY");

  private final AtomicBoolean updateRequested = new AtomicBoolean();

  public ExecutionPointHighlighter(@Nonnull Project project) {
    myProject = project;

    // Update highlighter colors if global color schema was changed
    project.getMessageBus().connect().subscribe(EditorColorsManager.TOPIC, scheme -> update(false));
  }

  public void show(final @Nonnull XSourcePosition position, final boolean notTopFrame, @Nullable final GutterIconRenderer gutterIconRenderer) {
    updateRequested.set(false);
    TransactionGuard.submitTransaction(myProject, () -> {
      updateRequested.set(false);

      mySourcePosition = position;

      clearDescriptor();
      myOpenFileDescriptor = XSourcePositionImpl.createOpenFileDescriptor(myProject, position);
      if (!XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().isScrollToCenter()) {
        myOpenFileDescriptor.setScrollType(notTopFrame ? ScrollType.CENTER : ScrollType.MAKE_VISIBLE);
      }
      //see IDEA-125645 and IDEA-63459
      //myOpenFileDescriptor.setUseCurrentWindow(true);

      myGutterIconRenderer = gutterIconRenderer;
      myNotTopFrame = notTopFrame;

      doShow(true);
    });
  }

  public void hide() {
    AppUIUtil.invokeOnEdt(() -> {
      updateRequested.set(false);

      removeHighlighter();
      clearDescriptor();
      myEditor = null;
      myGutterIconRenderer = null;
    });
  }

  private void clearDescriptor() {
    if (myOpenFileDescriptor != null) {
      myOpenFileDescriptor.dispose();
      myOpenFileDescriptor = null;
    }
  }

  public void navigateTo() {
    if (myOpenFileDescriptor != null && myOpenFileDescriptor.getFile().isValid()) {
      myOpenFileDescriptor.navigateInEditor(myProject, true);
    }
  }

  @Nullable
  public VirtualFile getCurrentFile() {
    return myOpenFileDescriptor != null ? myOpenFileDescriptor.getFile() : null;
  }

  public void update(final boolean navigate) {
    if (updateRequested.compareAndSet(false, true)) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (updateRequested.compareAndSet(true, false)) {
          doShow(navigate);
        }
      }, myProject.getDisposed());
    }
  }

  public void updateGutterIcon(@Nullable final GutterIconRenderer renderer) {
    AppUIUtil.invokeOnEdt(() -> {
      if (myRangeHighlighter != null && myGutterIconRenderer != null) {
        myRangeHighlighter.setGutterIconRenderer(renderer);
      }
    });
  }

  private void doShow(boolean navigate) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    removeHighlighter();


    OpenFileDescriptorImpl fileDescriptor = myOpenFileDescriptor;
    if (!navigate && myOpenFileDescriptor != null) {
      fileDescriptor = new OpenFileDescriptorImpl(myProject, myOpenFileDescriptor.getFile());
    }
    myEditor = null;
    if (fileDescriptor != null) {
      if (!navigate) {
        FileEditor editor = FileEditorManager.getInstance(fileDescriptor.getProject()).getSelectedEditor(fileDescriptor.getFile());
        if (editor instanceof TextEditor) {
          myEditor = ((TextEditor)editor).getEditor();
        }
      }
      if (myEditor == null) {
        myEditor = XDebuggerUtilImpl.createEditor(fileDescriptor);
      }
    }
    if (myEditor != null) {
      addHighlighter();
    }
  }

  private void removeHighlighter() {
    if (myEditor != null) {
      disableMouseHoverPopups(myEditor, false);
    }

    //if (myNotTopFrame && myEditor != null) {
    //  myEditor.getSelectionModel().removeSelection();
    //}

    if (myRangeHighlighter != null) {
      myRangeHighlighter.dispose();
      myRangeHighlighter = null;
    }
  }

  private void addHighlighter() {
    disableMouseHoverPopups(myEditor, true);
    int line = mySourcePosition.getLine();
    Document document = myEditor.getDocument();
    if (line < 0 || line >= document.getLineCount()) return;

    //if (myNotTopFrame) {
    //  myEditor.getSelectionModel().setSelection(document.getLineStartOffset(line), document.getLineEndOffset(line) + document.getLineSeparatorLength(line));
    //  return;
    //}

    if (myRangeHighlighter != null) return;

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes attributes = myNotTopFrame ? scheme.getAttributes(DebuggerColors.NOT_TOP_FRAME_ATTRIBUTES) : scheme.getAttributes(DebuggerColors.EXECUTIONPOINT_ATTRIBUTES);
    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, myProject, true);
    if (mySourcePosition instanceof HighlighterProvider) {
      TextRange range = ((HighlighterProvider)mySourcePosition).getHighlightRange();
      if (range != null) {
        TextRange lineRange = DocumentUtil.getLineTextRange(document, line);
        if (!range.equals(lineRange)) {
          myRangeHighlighter = markupModel.addRangeHighlighter(range.getStartOffset(), range.getEndOffset(), DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER, attributes, HighlighterTargetArea.EXACT_RANGE);
        }
      }
    }
    if (myRangeHighlighter == null) {
      myRangeHighlighter = markupModel.addLineHighlighter(line, DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER, attributes);
    }
    myRangeHighlighter.putUserData(EXECUTION_POINT_HIGHLIGHTER_TOP_FRAME_KEY, !myNotTopFrame);
    myRangeHighlighter.setGutterIconRenderer(myGutterIconRenderer);
  }

  public boolean isFullLineHighlighter() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myRangeHighlighter != null && myRangeHighlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE;
  }

  private static void disableMouseHoverPopups(@Nonnull final Editor editor, final boolean disable) {
    Project project = editor.getProject();
    if (ApplicationManager.getApplication().isUnitTestMode() || project == null) return;

    // need to always invoke later to maintain order of enabling/disabling
    SwingUtilities.invokeLater(() -> {
      if (disable) {
        EditorMouseHoverPopupControl.disablePopups(project);
      }
      else {
        EditorMouseHoverPopupControl.enablePopups(project);
      }
    });
  }

  public interface HighlighterProvider {
    @Nullable
    TextRange getHighlightRange();
  }
}
