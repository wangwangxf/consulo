package com.intellij.coverage.view;

import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import consulo.annotation.access.RequiredReadAction;
import consulo.project.Project;
import consulo.language.psi.PsiNamedElement;
import javax.annotation.Nonnull;

import java.util.Collection;
import java.util.List;

/**
 * User: anna
 * Date: 1/5/12
 */
public class CoverageListRootNode extends CoverageListNode {
  private List<AbstractTreeNode> myTopLevelPackages;
  private final Project myProject;

  public CoverageListRootNode(Project project, PsiNamedElement classOrPackage,
                              CoverageSuitesBundle bundle,
                              CoverageViewManager.StateBean stateBean) {
    super(project, classOrPackage, bundle, stateBean);
    myProject = classOrPackage.getProject();
  }

  private List<AbstractTreeNode> getTopLevelPackages(CoverageSuitesBundle bundle, CoverageViewManager.StateBean stateBean, Project project) {
    if (myTopLevelPackages == null) {
      myTopLevelPackages = bundle.getCoverageEngine().createCoverageViewExtension(project, bundle, stateBean).createTopLevelNodes();
      for (AbstractTreeNode abstractTreeNode : myTopLevelPackages) {
        abstractTreeNode.setParent(this);
      }
    }
    return myTopLevelPackages;
  }

  @RequiredReadAction
  @Nonnull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    if (myStateBean.myFlattenPackages) {
      return getTopLevelPackages(myBundle, myStateBean, myProject);
    }
    return super.getChildren();
  }
}
