// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import consulo.application.Application;
import consulo.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import consulo.project.Project;
import consulo.project.ProjectManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import consulo.container.boot.ContainerPathManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.util.collection.Sets;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class VfsRootAccess {
  private static final boolean SHOULD_PERFORM_ACCESS_CHECK = System.getenv("NO_FS_ROOTS_ACCESS_CHECK") == null && System.getProperty("NO_FS_ROOTS_ACCESS_CHECK") == null;

  // we don't want test subclasses to accidentally remove allowed files, added by base classes
  private static final Set<String> ourAdditionalRoots = Sets.newHashSet(FileUtil.PATH_HASHING_STRATEGY);
  private static boolean insideGettingRoots;

  @TestOnly
  static void assertAccessInTests(@Nonnull VirtualFileSystemEntry child, @Nonnull NewVirtualFileSystem delegate) {
    final Application application = ApplicationManager.getApplication();
    if (SHOULD_PERFORM_ACCESS_CHECK &&
        application.isUnitTestMode() &&
        application instanceof ApplicationEx &&
        !ApplicationInfoImpl.isInPerformanceTest()) {

      if (delegate != LocalFileSystem.getInstance() && !(delegate instanceof ArchiveFileSystem)) {
        return;
      }

      // root' children are loaded always
      if (child.getParent() == null || child.getParent().getParent() == null) {
        return;
      }

      Set<String> allowed = allowedRoots();
      boolean isUnder = allowed == null || allowed.isEmpty();

      if (!isUnder) {
        String childPath = child.getPath();
        if (delegate instanceof ArchiveFileSystem) {
          VirtualFile local = ((ArchiveFileSystem)delegate).getLocalVirtualFileFor(child);
          assert local != null : child;
          childPath = local.getPath();
        }
        for (String root : allowed) {
          if (FileUtil.startsWith(childPath, root)) {
            isUnder = true;
            break;
          }
          if (root.startsWith(StandardFileSystems.JAR_PROTOCOL_PREFIX)) {
            String rootLocalPath = FileUtil.toSystemIndependentName(PathUtil.toPresentableUrl(root));
            isUnder = FileUtil.startsWith(childPath, rootLocalPath);
            if (isUnder) break;
          }
        }
      }

      assert isUnder : "File accessed outside allowed roots: " + child + ";\nAllowed roots: " + new ArrayList<>(allowed);
    }
  }

  // null means we were unable to get roots, so do not check access
  private static Set<String> allowedRoots() {
    if (insideGettingRoots) return null;

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length == 0) return null;

    final Set<String> allowed = Sets.newHashSet(FileUtil.PATH_HASHING_STRATEGY);
    allowed.add(FileUtil.toSystemIndependentName(ContainerPathManager.get().getHomePath()));

    // In plugin development environment PathManager.getHomePath() returns path like "~/.IntelliJIdea/system/plugins-sandbox/test" when running tests
    // The following is to avoid errors in tests like "File accessed outside allowed roots: file://C:/Program Files/idea/lib/idea.jar"
    //final String homePath2 = PathManager.getHomePathFor(Application.class);
    //if (homePath2 != null) {
    //  allowed.add(FileUtil.toSystemIndependentName(homePath2));
    //}

    try {
      URL outUrl = Application.class.getResource("/");
      if (outUrl != null) {
        String output = new File(outUrl.toURI()).getParentFile().getParentFile().getPath();
        allowed.add(FileUtil.toSystemIndependentName(output));
      }
    }
    catch (URISyntaxException | IllegalArgumentException ignored) {
    }

    try {
      String javaHome = SystemProperties.getJavaHome();
      allowed.add(FileUtil.toSystemIndependentName(javaHome));
      allowed.add(FileUtil.toSystemIndependentName(new File(FileUtil.getTempDirectory()).getParent()));
      allowed.add(FileUtil.toSystemIndependentName(System.getProperty("java.io.tmpdir")));
      allowed.add(FileUtil.toSystemIndependentName(SystemProperties.getUserHome()));
      ContainerUtil.addAllNotNull(allowed, findInUserHome(".m2"));
      ContainerUtil.addAllNotNull(allowed, findInUserHome(".gradle"));

      // see IDEA-167037 The assertion "File accessed outside allowed root" is triggered by files symlinked from the the JDK installation folder
      allowed.add("/etc"); // After recent update of Oracle JDK 1.8 under Ubuntu Certain files in the JDK installation are symlinked to /etc
      allowed.add("/private/etc");

      for (final Project project : openProjects) {
        if (!project.isInitialized()) {
          return null; // all is allowed
        }
        for (String url : ProjectRootManager.getInstance(project).getContentRootUrls()) {
          allowed.add(VfsUtilCore.urlToPath(url));
        }
        for (String url : getAllRootUrls(project)) {
          allowed.add(StringUtil.trimEnd(VfsUtilCore.urlToPath(url), ArchiveFileSystem.ARCHIVE_SEPARATOR));
        }
        String location = project.getBasePath();
        assert location != null : project;
        allowed.add(FileUtil.toSystemIndependentName(location));
      }
    }
    catch (Error ignored) {
      // sometimes library.getRoots() may crash if called from inside library modification
    }
    allowed.addAll(ourAdditionalRoots);

    return allowed;
  }

  @Nullable
  private static String findInUserHome(@Nonnull String path) {
    try {
      // in case if we have a symlink like ~/.m2 -> /opt/.m2
      return FileUtil.toSystemIndependentName(new File(SystemProperties.getUserHome(), path).getCanonicalPath());
    }
    catch (IOException e) {
      return null;
    }
  }

  @Nonnull
  private static Collection<String> getAllRootUrls(@Nonnull Project project) {
    insideGettingRoots = true;
    final Set<String> roots = new HashSet<>();

    OrderEnumerator enumerator = ProjectRootManager.getInstance(project).orderEntries().using(DefaultModulesProvider.of(project));
    ContainerUtil.addAll(roots, enumerator.classes().getUrls());
    ContainerUtil.addAll(roots, enumerator.sources().getUrls());

    insideGettingRoots = false;
    return roots;
  }

  @TestOnly
  public static void allowRootAccess(@Nonnull Disposable disposable, @Nonnull final String... roots) {
    if (roots.length == 0) return;
    allowRootAccess(roots);
    Disposer.register(disposable, () -> disallowRootAccess(roots));
  }

  /**
   * @deprecated Use {@link #allowRootAccess(Disposable, String...)} instead
   */
  @Deprecated
  @TestOnly
  public static void allowRootAccess(@Nonnull String... roots) {
    for (String root : roots) {
      ourAdditionalRoots.add(StringUtil.trimEnd(FileUtil.toSystemIndependentName(root), '/'));
    }
  }

  /**
   * @deprecated Use {@link #allowRootAccess(Disposable, String...)} instead
   */
  @Deprecated
  @TestOnly
  public static void disallowRootAccess(@Nonnull String... roots) {
    for (String root : roots) {
      ourAdditionalRoots.remove(StringUtil.trimEnd(FileUtil.toSystemIndependentName(root), '/'));
    }
  }
}