// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.application;

import consulo.component.ComponentManager;
import consulo.disposer.Disposable;
import consulo.component.ProcessCanceledException;
import consulo.application.progress.ProgressIndicator;
import consulo.ui.ModalityState;
import consulo.util.concurrent.CancellablePromise;
import consulo.util.dataholder.Key;
import org.jetbrains.annotations.Contract;

import javax.annotation.Nonnull;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A utility for running non-blocking read actions in background thread.
 * "Non-blocking" means to prevent UI freezes, when a write action is about to occur, a read action can be interrupted by a
 * {@link ProcessCanceledException} and then restarted.
 *
 * @see ReadAction#nonBlocking
 */
public interface NonBlockingReadAction<T> {

  /**
   * @return a copy of this builder that runs read actions only when index is available in the given project.
   * The operation is canceled if the project is closed before either the background computation or {@link #finishOnUiThread} runnable
   * are completed.
   * @see consulo.ide.impl.idea.openapi.project.DumbService
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> inSmartMode(@Nonnull ComponentManager project);

  /**
   * @return a copy of this builder that runs read actions only when all documents are committed.
   * The operation is canceled if the project is closed before either the background computation or {@link #finishOnUiThread} runnable
   * are completed.
   * @see com.intellij.psi.PsiDocumentManager
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> withDocumentsCommitted(@Nonnull ComponentManager project);

  /**
   * @return a copy of this builder that cancels submitted read actions after they become obsolete.
   * An action is considered obsolete if any of the conditions provided using {@code expireWhen} returns true).
   * The conditions are checked inside a read action, either on a background or on the UI thread.
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> expireWhen(@Nonnull BooleanSupplier expireCondition);

  /**
   * @return a copy of this builder that cancels submitted read actions once the specified progress indicator is cancelled.
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> cancelWith(@Nonnull ProgressIndicator progressIndicator);

  /**
   * @return a copy of this builder that cancels submitted read actions once the specified disposable is disposed.
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> expireWith(@Nonnull Disposable parentDisposable);

  /**
   * @return a copy of this builder that completes submitted read actions on UI thread with the given modality state.
   * The read actions are still executed on background thread, but the callbacks on their completion
   * are invoked on UI thread, and no write action is allowed to interfere before that and possibly invalidate the result.
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> finishOnUiThread(@Nonnull ModalityState modality, @Nonnull Consumer<T> uiThreadAction);

  /**
   * Merges together similar computations by cancelling the previous ones when a new one is submitted.
   * This can be useful when the results of the previous computation won't make sense anyway in the changed environment.
   *
   * @param equality objects that together identify the computation: if they're all equal in two submissions,
   *                 then the computations are merged. Callers should take care to pass something unique there
   *                 (e.g. some {@link Key} or {@code this} {@code getClass()}),
   *                 so that computations from different places won't interfere.
   * @return a copy of this builder which, when submitted, cancels previously submitted running computations with equal equality objects
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> coalesceBy(@Nonnull Object... equality);

  /**
   * Submit this computation to be performed in a non-blocking read action on background thread. The returned promise
   * is completed on the same thread (in the same read action), or on UI thread if {@link #finishOnUiThread} has been called.
   *
   * @param backgroundThreadExecutor an executor to actually run the computation. Common examples are
   *                                 {@link consulo.ide.impl.idea.util.concurrency.NonUrgentExecutor#getInstance()} or
   *                                 {@link AppExecutorUtil#getAppExecutorService()} or
   *                                 {@link consulo.ide.impl.idea.util.concurrency.BoundedTaskExecutor} on top of that.
   */
  CancellablePromise<T> submit(@Nonnull Executor backgroundThreadExecutor);
}
