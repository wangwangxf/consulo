// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.codeInsight.completion;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.document.event.DocumentEvent;
import consulo.document.event.DocumentListener;
import consulo.document.impl.event.DocumentEventImpl;
import consulo.language.editor.completion.internal.OffsetTranslator;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiModificationTrackerListener;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedList;

/**
 * @author peter
 */
public class OffsetTranslatorImpl implements Disposable, OffsetTranslator {
  private final PsiFile myOriginalFile;
  private final Document myCopyDocument;
  private final LinkedList<DocumentEvent> myTranslation = new LinkedList<>();

  public OffsetTranslatorImpl(Document originalDocument, PsiFile originalFile, Document copyDocument, int start, int end, String replacement) {
    myOriginalFile = originalFile;
    myCopyDocument = copyDocument;
    myCopyDocument.putUserData(RANGE_TRANSLATION, this);
    myTranslation.addFirst(new DocumentEventImpl(copyDocument, start, originalDocument.getImmutableCharSequence().subSequence(start, end), replacement, 0, false));
    Disposer.register(originalFile.getProject(), this);

    final LinkedList<DocumentEvent> sinceCommit = new LinkedList<>();
    originalDocument.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@Nonnull DocumentEvent e) {
        if (isUpToDate()) {
          DocumentEventImpl inverse = new DocumentEventImpl(originalDocument, e.getOffset(), e.getNewFragment(), e.getOldFragment(), 0, false);
          sinceCommit.addLast(inverse);
        }
      }
    }, this);

    originalFile.getProject().getMessageBus().connect(this).subscribe(PsiModificationTrackerListener.class, new PsiModificationTrackerListener() {
      final long lastModCount = originalFile.getViewProvider().getModificationStamp();

      @Override
      public void modificationCountChanged() {
        if (isUpToDate() && lastModCount != originalFile.getViewProvider().getModificationStamp()) {
          myTranslation.addAll(sinceCommit);
          sinceCommit.clear();
        }
      }
    });

  }

  private boolean isUpToDate() {
    return this == myCopyDocument.getUserData(RANGE_TRANSLATION) && myOriginalFile.isValid();
  }

  @Override
  public void dispose() {
    if (isUpToDate()) {
      myCopyDocument.putUserData(RANGE_TRANSLATION, null);
    }
  }

  @Override
  @Nullable
  public Integer translateOffset(Integer offset) {
    for (DocumentEvent event : myTranslation) {
      offset = translateOffset(offset, event);
      if (offset == null) {
        return null;
      }
    }
    return offset;
  }

  @Nullable
  private static Integer translateOffset(int offset, DocumentEvent event) {
    if (event.getOffset() < offset && offset < event.getOffset() + event.getNewLength()) {
      if (event.getOldLength() == 0) {
        return event.getOffset();
      }

      return null;
    }

    return offset <= event.getOffset() ? offset : offset - event.getNewLength() + event.getOldLength();
  }

}
