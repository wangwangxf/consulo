// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.openapi.editor.impl;

import consulo.codeEditor.SoftWrapModelEx;
import consulo.codeEditor.impl.softwrap.SoftWrapPainter;
import consulo.codeEditor.impl.softwrap.SoftWrapsStorage;
import consulo.codeEditor.impl.softwrap.mapping.CachingSoftWrapDataMapper;
import consulo.ide.impl.idea.openapi.editor.impl.softwrap.mapping.DesktopSoftWrapApplianceManager;
import consulo.annotation.DeprecationInfo;
import consulo.codeEditor.impl.CodeEditorBase;
import consulo.codeEditor.impl.CodeEditorSoftWrapModelBase;
import consulo.codeEditor.impl.softwrap.mapping.SoftWrapApplianceManager;

import javax.annotation.Nonnull;

/**
 * Default {@link SoftWrapModelEx} implementation.
 * <p/>
 * Works as a mix of {@code GoF Facade and Bridge}, i.e. delegates the processing to the target sub-components and provides
 * utility methods built on top of sub-components API.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 */
@Deprecated
@DeprecationInfo("Desktop only")
@SuppressWarnings("deprecation")
public class SoftWrapModelImpl extends CodeEditorSoftWrapModelBase {
  SoftWrapModelImpl(@Nonnull CodeEditorBase editor) {
    super(editor);
  }

  @Override
  protected SoftWrapApplianceManager createSoftWrapApplianceManager(@Nonnull SoftWrapsStorage storage,
                                                                    @Nonnull CodeEditorBase editor,
                                                                    @Nonnull SoftWrapPainter painter,
                                                                    CachingSoftWrapDataMapper dataMapper) {
    return new DesktopSoftWrapApplianceManager(storage, editor, painter, dataMapper);
  }
}
