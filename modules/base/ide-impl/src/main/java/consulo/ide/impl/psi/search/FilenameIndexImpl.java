/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package consulo.ide.impl.psi.search;

import consulo.ide.impl.idea.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import consulo.index.io.DataIndexer;
import consulo.index.io.EnumeratorStringDescriptor;
import consulo.index.io.KeyDescriptor;
import consulo.index.io.ID;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.stub.FileContent;
import consulo.language.psi.stub.ScalarIndexExtension;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

import java.util.Collections;

public class FilenameIndexImpl extends ScalarIndexExtension<String> {
  @NonNls
  static final ID<String, Void> NAME = FilenameIndex.NAME;

  @Nonnull
  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  @Nonnull
  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return inputData -> Collections.singletonMap(inputData.getFileName(), null);
  }

  @Nonnull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Nonnull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return (project, file) -> file instanceof VirtualFileSystemEntry;
  }

  @Override
  public boolean dependsOnFileContent() {
    return false;
  }

  @Override
  public boolean indexDirectories() {
    return true;
  }

  @Override
  public int getVersion() {
    return 3;
  }

  @Override
  public boolean traceKeyHashToVirtualFileMapping() {
    return true;
  }
}
