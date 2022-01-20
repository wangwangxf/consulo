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
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.components.ServiceManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogUserFilter;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.data.index.IndexedDetails;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.util.VcsUserUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class IndexSpeedSearch extends VcsLogSpeedSearch {
  @Nonnull
  private final VcsLogIndex myIndex;
  @Nonnull
  private final VcsUserRegistry myUserRegistry;

  @Nullable private Set<Integer> myMatchedByUserCommits;
  @Nullable private Collection<VcsUser> myMatchedUsers;

  public IndexSpeedSearch(@Nonnull Project project, @Nonnull VcsLogIndex index, @Nonnull VcsLogGraphTable component) {
    super(component);
    myIndex = index;
    myUserRegistry = ServiceManager.getService(project, VcsUserRegistry.class);

    addChangeListener(evt -> {
      if (evt.getPropertyName().equals(ENTERED_PREFIX_PROPERTY_NAME)) {
        String newValue = (String)evt.getNewValue();
        if (newValue != null) {
          String oldValue = (String)evt.getOldValue();
          Collection<VcsUser> usersToExamine = myUserRegistry.getUsers();
          if (oldValue != null && newValue.contains(oldValue) && myMatchedUsers != null) {
            if (myMatchedUsers.isEmpty()) return;
            usersToExamine = myMatchedUsers;
          }
          myMatchedUsers = ContainerUtil.filter(usersToExamine,
                                                user -> compare(VcsUserUtil.getShortPresentation(user), newValue));
          myMatchedByUserCommits = myIndex.filter(Collections.singletonList(new SimpleVcsLogUserFilter(myMatchedUsers)));
        }
        else {
          myMatchedByUserCommits = null;
          myMatchedUsers = null;
        }
      }
    });
  }

  @Override
  protected boolean isSpeedSearchEnabled() {
    if (super.isSpeedSearchEnabled()) {
      VisiblePack visiblePack = myComponent.getModel().getVisiblePack();
      Set<VirtualFile> roots = visiblePack.getLogProviders().keySet();
      Set<VirtualFile> visibleRoots = VcsLogUtil.getAllVisibleRoots(roots, visiblePack.getFilters().getRootFilter(),
                                                                    visiblePack.getFilters().getStructureFilter());
      for (VirtualFile root : visibleRoots) {
        if (!myIndex.isIndexed(root)) return false;
      }
      return true;
    }
    return false;
  }

  @Nullable
  @Override
  protected String getElementText(@Nonnull Object row) {
    throw new UnsupportedOperationException(
            "Getting row text in a Log is unsupported since we match commit subject and author separately.");
  }

  @Nullable
  private String getCommitSubject(@Nonnull Integer row) {
    Integer id = myComponent.getModel().getIdAtRow(row);
    String message = myIndex.getFullMessage(id);
    if (message == null) return super.getElementText(row);
    return IndexedDetails.getSubject(message);
  }

  @Override
  protected boolean isMatchingElement(Object row, String pattern) {
    String str = getCommitSubject((Integer)row);
    return (str != null && compare(str, pattern)) ||
           (myMatchedByUserCommits != null &&
            !myMatchedByUserCommits.isEmpty() &&
            // getting id from row takes time, so optimizing a little here
            myMatchedByUserCommits.contains(myComponent.getModel().getIdAtRow((Integer)row)));
  }

  private static class SimpleVcsLogUserFilter implements VcsLogUserFilter {
    @Nonnull
    private final Collection<VcsUser> myMatchedUsers;

    public SimpleVcsLogUserFilter(@Nonnull Collection<VcsUser> matchedUsers) {
      myMatchedUsers = matchedUsers;
    }

    @Nonnull
    @Override
    public Collection<VcsUser> getUsers(@Nonnull VirtualFile root) {
      return myMatchedUsers;
    }

    @Override
    public boolean matches(@Nonnull VcsCommitMetadata details) {
      return myMatchedUsers.contains(details.getAuthor());
    }
  }
}
