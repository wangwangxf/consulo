package org.jetbrains.idea.maven.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.*;

import java.util.*;

public class MavenProjectModel {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.project.MavenProjectModel");

  @NotNull
  private final List<Node> rootProjects = new ArrayList<Node>();

  public MavenProjectModel(Collection<VirtualFile> filesToImport,
                           Map<VirtualFile, Module> existingModules,
                           Collection<String> profiles,
                           MavenProjectReader projectReader,
                           Progress p) throws MavenException, CanceledException {
    Map<VirtualFile, Module> allFilesToImport = new HashMap<VirtualFile, Module>();

    for (Map.Entry<VirtualFile, Module> entry : existingModules.entrySet()) {
      allFilesToImport.put(entry.getKey(), entry.getValue());
    }

    for (VirtualFile file : filesToImport) {
      allFilesToImport.put(file, null);
    }

    while (allFilesToImport.size() != 0) {
      p.checkCanceled();

      VirtualFile nextFile = allFilesToImport.keySet().iterator().next();
      MavenProjectModel.Node node = createMavenTree(projectReader,
                                                    nextFile,
                                                    allFilesToImport,
                                                    profiles,
                                                    true,
                                                    p);
      rootProjects.add(node);
    }
  }

  @NotNull
  public List<Node> getRootProjects() {
    return rootProjects;
  }

  private Node createMavenTree(MavenProjectReader reader,
                               VirtualFile pomFile,
                               Map<VirtualFile, Module> unprocessedFiles,
                               Collection<String> profiles,
                               boolean isExistingModuleTree,
                               Progress p) throws MavenException, CanceledException {
    Module existingModule = unprocessedFiles.get(pomFile);
    unprocessedFiles.remove(pomFile);

    p.checkCanceled();
    p.setText(ProjectBundle.message("maven.reading.pom", FileUtil.toSystemDependentName(pomFile.getPath())));

    MavenProject mavenProject = reader.readBare(pomFile.getPath());

    if (existingModule == null) isExistingModuleTree = false;
    if (!isExistingModuleTree) existingModule = null;

    Node node = new Node(pomFile, mavenProject, existingModule);

    createChildNodes(reader,
                     pomFile,
                     unprocessedFiles,
                     profiles,
                     mavenProject,
                     node,
                     isExistingModuleTree,
                     p);
    return node;
  }

  private void createChildNodes(MavenProjectReader reader,
                                VirtualFile pomFile,
                                Map<VirtualFile, Module> unprocessedFiles,
                                Collection<String> profiles,
                                MavenProject mavenProject,
                                Node parentNode,
                                boolean isExistingModuleTree,
                                Progress p) throws MavenException, CanceledException {
    for (String modulePath : ProjectUtil.collectRelativeModulePaths(mavenProject, profiles, new HashSet<String>())) {
      p.checkCanceled();

      VirtualFile childFile = getMavenModuleFile(pomFile, modulePath);

      if (childFile == null) {
        LOG.info("Cannot find maven module " + modulePath);
        continue;
      }

      Node existingRoot = findExistingRoot(childFile);
      if (existingRoot != null) {
        rootProjects.remove(existingRoot);
        parentNode.mavenModules.add(existingRoot);
      }
      else {
        Node module = createMavenTree(reader,
                                      childFile,
                                      unprocessedFiles,
                                      profiles,
                                      isExistingModuleTree,
                                      p);
        parentNode.mavenModules.add(module);
      }
    }
  }

  @Nullable
  private static VirtualFile getMavenModuleFile(VirtualFile parentPom, String moduleRelPath) {
    final VirtualFile parentDir = parentPom.getParent();
    if (parentDir != null) {
      VirtualFile moduleDir = parentDir.findFileByRelativePath(moduleRelPath);
      if (moduleDir != null) {
        return moduleDir.findChild(Constants.POM_XML);
      }
    }
    return null;
  }

  private Node findExistingRoot(final VirtualFile childFile) {
    return visit(new MavenProjectVisitor<Node>() {
      public void visit(final Node node) {
        if (node.getFile() == childFile) {
          setResult(node);
        }
      }

      public Iterable<Node> getChildren(final Node node) {
        return null;
      }
    });
  }

  public void resolve(final MavenProjectReader projectReader,
                      final List<String> profiles,
                      final Progress p) throws MavenException, CanceledException {
    final MavenException[] mavenEx = new MavenException[1];
    final CanceledException[] canceledEx = new CanceledException[1];

    visit(new MavenProjectVisitorPlain() {
      public void visit(Node node) {
        try {
          p.checkCanceled();
          p.setText(ProjectBundle.message("maven.resolving", FileUtil.toSystemDependentName(node.getPath())));

          node.resolve(projectReader, profiles);
        }
        catch (MavenException e) {
          mavenEx[0] = e;
        }
        catch (CanceledException e) {
          canceledEx[0] = e;
        }
      }
    });

    if (mavenEx[0] != null) throw mavenEx[0];
    if (canceledEx[0] != null) throw canceledEx[0];
  }

  abstract static class MavenProjectVisitor<Result> extends Tree.VisitorAdapter<Node, Result> {
    public boolean shouldVisit(Node node) {
      return node.isIncluded();
    }

    public Iterable<Node> getChildren(Node node) {
      return node.mavenModules;
    }
  }

  public abstract static class MavenProjectVisitorPlain extends MavenProjectVisitor<Object> {
  }

  public abstract static class MavenProjectVisitorRoot extends MavenProjectVisitorPlain {
    public Iterable<Node> getChildren(final Node node) {
      return null;
    }
  }

  public <Result> Result visit(MavenProjectVisitor<Result> visitor) {
    return Tree.visit(rootProjects, visitor);
  }

  public static class Node {
    @NotNull private final VirtualFile pomFile;
    @NotNull private MavenProject mavenProject;
    private Module linkedModule;

    private boolean included = true;

    final List<Node> mavenModules = new ArrayList<Node>();
    final List<Node> mavenModulesTopoSorted = new ArrayList<Node>(); // recursive

    private Node(@NotNull VirtualFile pomFile, @NotNull final MavenProject mavenProject, final Module linkedModule) {
      this.pomFile = pomFile;
      this.mavenProject = mavenProject;
      this.linkedModule = linkedModule;
    }

    public VirtualFile getFile() {
      return pomFile;
    }

    @NotNull
    public String getPath() {
      return pomFile.getPath();
    }

    @SuppressWarnings({"ConstantConditions"})
    @NotNull
    public String getDirectory() {
      return pomFile.getParent().getPath();
    }

    @NotNull
    public MavenProject getMavenProject() {
      return mavenProject;
    }

    public Artifact getArtifact() {
      return mavenProject.getArtifact();
    }

    public MavenId getId() {
      return new MavenId(getArtifact());
    }

    public boolean isIncluded() {
      return included;
    }

    public void setIncluded(final boolean included) {
      this.included = included;
    }

    public Module getLinkedModule() {
      return linkedModule;
    }

    public boolean isLinked() {
      return linkedModule != null;
    }

    public void unlinkModule() {
      linkedModule = null;
    }

    public void resolve(final MavenProjectReader projectReader, final List<String> profiles) throws MavenException, CanceledException {
      List<MavenProject> resolvedModules = new ArrayList<MavenProject>();
      mavenProject = projectReader.readResolved(getPath(), profiles, resolvedModules);

      Map<String,Node> pathToNode = createPathToNodeMap(mavenModules, new HashMap<String, Node>());

      mavenModulesTopoSorted.clear();
      for (MavenProject resolvedModule : resolvedModules) {
        Node node = pathToNode.get(getNormalizedPath(resolvedModule));
        if(node!=null){
          node.mavenProject = resolvedModule;
          mavenModulesTopoSorted.add(node);
        }
      }
    }
  }

  private static Map<String, Node> createPathToNodeMap(final List<Node> mavenModules, final Map<String, Node> pathToNode) {
    for (Node mavenModule : mavenModules) {
      pathToNode.put(getNormalizedPath(mavenModule.getMavenProject()), mavenModule);
      createPathToNodeMap(mavenModule.mavenModules, pathToNode);
    }
    return pathToNode;
  }

  private static String getNormalizedPath(MavenProject mavenProject) {
    return new Path(mavenProject.getFile().getPath()).getPath();
  }
}
