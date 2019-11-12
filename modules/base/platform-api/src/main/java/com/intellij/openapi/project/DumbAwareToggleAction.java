// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.project;

import com.intellij.openapi.actionSystem.ToggleAction;
import consulo.ui.image.Image;
import consulo.ui.migration.SwingImageRef;

import javax.annotation.Nullable;
import javax.swing.*;

public abstract class DumbAwareToggleAction extends ToggleAction implements DumbAware {
  protected DumbAwareToggleAction() {
  }

  protected DumbAwareToggleAction(@Nullable String text) {
    super(text);
  }

  @Deprecated
  protected DumbAwareToggleAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  protected DumbAwareToggleAction(@Nullable String text, @Nullable String description, @Nullable Image icon) {
    super(text, description, icon);
  }

  protected DumbAwareToggleAction(@Nullable String text, @Nullable String description, @Nullable SwingImageRef icon) {
    super(text, description, icon);
  }
}