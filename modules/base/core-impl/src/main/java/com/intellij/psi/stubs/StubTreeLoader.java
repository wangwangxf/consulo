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
package com.intellij.psi.stubs;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.Function;
import consulo.application.internal.PerApplicationInstance;
import consulo.logging.attachment.Attachment;
import consulo.logging.attachment.AttachmentFactory;
import consulo.logging.attachment.RuntimeExceptionWithAttachments;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public abstract class StubTreeLoader {
  private static final PerApplicationInstance<StubTreeLoader> ourInstance = PerApplicationInstance.of(StubTreeLoader.class);

  @Nonnull
  public static StubTreeLoader getInstance() {
    return ourInstance.get();
  }

  @Nullable
  public abstract ObjectStubTree readOrBuild(Project project, final VirtualFile vFile, @Nullable final PsiFile psiFile);

  @Nullable
  public abstract ObjectStubTree readFromVFile(Project project, final VirtualFile vFile);

  public boolean isStubReloadingProhibited() {
    return false;
  }

  public abstract void rebuildStubTree(VirtualFile virtualFile);

  public abstract boolean canHaveStub(VirtualFile file);

  protected boolean hasPsiInManyProjects(@Nonnull VirtualFile virtualFile) {
    return false;
  }

  @Nullable
  protected IndexingStampInfo getIndexingStampInfo(@Nonnull VirtualFile file) {
    return null;
  }

  @Nonnull
  public RuntimeException stubTreeAndIndexDoNotMatch(@Nullable ObjectStubTree stubTree, @Nonnull PsiFileWithStubSupport psiFile, @Nullable Throwable cause) {
    VirtualFile file = psiFile.getViewProvider().getVirtualFile();
    StubTree stubTreeFromIndex = (StubTree)readFromVFile(psiFile.getProject(), file);
    boolean compiled = psiFile instanceof PsiCompiledElement;
    Document document = compiled ? null : FileDocumentManager.getInstance().getDocument(file);
    IndexingStampInfo indexingStampInfo = getIndexingStampInfo(file);
    boolean upToDate = indexingStampInfo != null && indexingStampInfo.isUpToDate(document, file, psiFile);

    boolean canBePrebuilt = isPrebuilt(psiFile.getVirtualFile());

    String msg = "PSI and index do not match.\nPlease report the problem to JetBrains with the files attached\n";

    if (canBePrebuilt) {
      msg += "This stub can have pre-built origin\n";
    }

    if (upToDate) {
      msg += "INDEXED VERSION IS THE CURRENT ONE";
    }

    msg += " file=" + psiFile;
    msg += ", file.class=" + psiFile.getClass();
    msg += ", file.lang=" + psiFile.getLanguage();
    msg += ", modStamp=" + psiFile.getModificationStamp();

    if (!compiled) {
      String text = psiFile.getText();
      PsiFile fromText = PsiFileFactory.getInstance(psiFile.getProject()).createFileFromText(psiFile.getName(), psiFile.getFileType(), text);
      if (fromText.getLanguage().equals(psiFile.getLanguage())) {
        boolean consistent = DebugUtil.psiToString(psiFile, true).equals(DebugUtil.psiToString(fromText, true));
        if (consistent) {
          msg += "\n tree consistent";
        }
        else {
          msg += "\n AST INCONSISTENT, perhaps after incremental reparse; " + fromText;
        }
      }
    }

    if (stubTree != null) {
      msg += "\n stub debugInfo=" + stubTree.getDebugInfo();
    }

    msg += "\nlatestIndexedStub=" + stubTreeFromIndex;
    if (stubTreeFromIndex != null) {
      if (stubTree != null) {
        msg += "\n   same size=" + (stubTree.getPlainList().size() == stubTreeFromIndex.getPlainList().size());
      }
      msg += "\n   debugInfo=" + stubTreeFromIndex.getDebugInfo();
    }

    FileViewProvider viewProvider = psiFile.getViewProvider();
    msg += "\n viewProvider=" + viewProvider;
    msg += "\n viewProvider stamp: " + viewProvider.getModificationStamp();

    msg += "; file stamp: " + file.getModificationStamp();
    msg += "; file modCount: " + file.getModificationCount();
    msg += "; file length: " + file.getLength();

    if (document != null) {
      msg += "\n doc saved: " + !FileDocumentManager.getInstance().isDocumentUnsaved(document);
      msg += "; doc stamp: " + document.getModificationStamp();
      msg += "; doc size: " + document.getTextLength();
      msg += "; committed: " + PsiDocumentManager.getInstance(psiFile.getProject()).isCommitted(document);
    }

    msg += "\nindexing info: " + indexingStampInfo;

    Attachment[] attachments = createAttachments(stubTree, psiFile, file, stubTreeFromIndex);

    // separate methods and separate exception classes for EA to treat these situations differently
    return hasPsiInManyProjects(file)
           ? handleManyProjectsMismatch(msg, attachments, cause)
           : upToDate ? handleUpToDateMismatch(msg, attachments, cause) : new RuntimeExceptionWithAttachments(msg, cause, attachments);
  }

  protected boolean isPrebuilt(@Nonnull VirtualFile virtualFile) {
    return false;
  }

  private static RuntimeExceptionWithAttachments handleManyProjectsMismatch(@Nonnull String message, Attachment[] attachments, @Nullable Throwable cause) {
    return new ManyProjectsStubIndexMismatch(message, cause, attachments);
  }
  
  private static RuntimeExceptionWithAttachments handleUpToDateMismatch(@Nonnull String message, Attachment[] attachments, @Nullable Throwable cause) {
    return new UpToDateStubIndexMismatch(message, cause, attachments);
  }

  @Nonnull
  private static Attachment[] createAttachments(@Nullable ObjectStubTree stubTree, @Nonnull PsiFileWithStubSupport psiFile, VirtualFile file, @Nullable StubTree stubTreeFromIndex) {
    List<Attachment> attachments = new ArrayList<>();
    attachments.add(AttachmentFactory.get().create(file.getPath() + "_file.txt", psiFile instanceof PsiCompiledElement ? "compiled" : psiFile.getText()));
    if (stubTree != null) {
      attachments.add(AttachmentFactory.get().create("stubTree.txt", ((PsiFileStubImpl)stubTree.getRoot()).printTree()));
    }
    if (stubTreeFromIndex != null) {
      attachments.add(AttachmentFactory.get().create("stubTreeFromIndex.txt", ((PsiFileStubImpl)stubTreeFromIndex.getRoot()).printTree()));
    }
    return attachments.toArray(Attachment.EMPTY_ARRAY);
  }
 
  public static String getFileViewProviderMismatchDiagnostics(@Nonnull FileViewProvider provider) {
    Function<PsiFile, String> fileClassName = file -> file.getClass().getSimpleName();
    Function<Pair<IStubFileElementType, PsiFile>, String> stubRootToString = pair -> "(" + pair.first.toString() + ", " + pair.first.getLanguage() + " -> " + fileClassName.fun(pair.second) + ")";
    List<Pair<IStubFileElementType, PsiFile>> roots = StubTreeBuilder.getStubbedRoots(provider);
    return "path = " +
           provider.getVirtualFile().getPath() +
           ", stubBindingRoot = " +
           fileClassName.fun(provider.getStubBindingRoot()) +
           ", languages = [" +
           StringUtil.join(provider.getLanguages(), Language::getID, ", ") +
           "], fileTypes = [" +
           StringUtil.join(provider.getAllFiles(), file -> file.getFileType().getName(), ", ") +
           "], files = [" +
           StringUtil.join(provider.getAllFiles(), fileClassName, ", ") +
           "], roots = [" +
           StringUtil.join(roots, stubRootToString, ", ") +
           "]";
  }
}

@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
class UpToDateStubIndexMismatch extends RuntimeExceptionWithAttachments {
  UpToDateStubIndexMismatch(String message, Throwable cause, Attachment... attachments) {
    super(message, cause, attachments);
  }
}

@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
class ManyProjectsStubIndexMismatch extends RuntimeExceptionWithAttachments {
  ManyProjectsStubIndexMismatch(String message, Throwable cause, Attachment... attachments) {
    super(message, cause, attachments);
  }
}
