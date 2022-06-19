/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package consulo.fileEditor.event;

import consulo.annotation.component.Topic;
import consulo.annotation.component.TopicBroadcastDirection;
import consulo.component.messagebus.TopicImpl;
import consulo.fileEditor.FileEditorManager;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import java.util.EventListener;

@Topic(direction = TopicBroadcastDirection.TO_PARENT)
public interface FileEditorManagerListener extends EventListener {

  default void fileOpened(@Nonnull FileEditorManager source, @Nonnull VirtualFile file) {
  }

  default void fileClosed(@Nonnull FileEditorManager source, @Nonnull VirtualFile file) {
  }

  default void selectionChanged(@Nonnull FileEditorManagerEvent event) {
  }

  interface Before extends EventListener {
    TopicImpl<Before> FILE_EDITOR_MANAGER = new TopicImpl<>("file editor before events", Before.class, TopicBroadcastDirection.TO_PARENT);

    void beforeFileOpened(@Nonnull FileEditorManager source, @Nonnull VirtualFile file);

    void beforeFileClosed(@Nonnull FileEditorManager source, @Nonnull VirtualFile file);

    class Adapter implements Before {
      @Override
      public void beforeFileOpened(@Nonnull FileEditorManager source, @Nonnull VirtualFile file) {
      }

      @Override
      public void beforeFileClosed(@Nonnull FileEditorManager source, @Nonnull VirtualFile file) {
      }
    }
  }
}