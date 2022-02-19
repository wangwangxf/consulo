package com.intellij.remoteServer.impl.configuration.deploySource.impl;

import com.intellij.openapi.util.io.FileUtil;
import consulo.compiler.artifact.Artifact;
import consulo.compiler.artifact.ArtifactPointer;
import consulo.compiler.artifact.element.ArtifactRootElement;
import consulo.compiler.artifact.element.CompositePackagingElement;
import com.intellij.remoteServer.configuration.deployment.ArtifactDeploymentSource;
import com.intellij.remoteServer.configuration.deployment.DeploymentSourceType;
import com.intellij.remoteServer.impl.configuration.deploySource.ArtifactDeploymentSourceType;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import java.io.File;

/**
 * @author nik
 */
public class ArtifactDeploymentSourceImpl implements ArtifactDeploymentSource {
  private final ArtifactPointer myPointer;

  public ArtifactDeploymentSourceImpl(@Nonnull ArtifactPointer pointer) {
    myPointer = pointer;
  }

  @Nonnull
  @Override
  public ArtifactPointer getArtifactPointer() {
    return myPointer;
  }

  @Override
  public Artifact getArtifact() {
    return myPointer.get();
  }

  @Override
  public File getFile() {
    final String path = getFilePath();
    return path != null ? new File(path) : null;
  }

  @Override
  public String getFilePath() {
    final Artifact artifact = getArtifact();
    if (artifact != null) {
      String outputPath = artifact.getOutputPath();
      if (outputPath != null) {
        final CompositePackagingElement<?> rootElement = artifact.getRootElement();
        if (!(rootElement instanceof ArtifactRootElement<?>)) {
          outputPath += "/" + rootElement.getName();
        }
        return FileUtil.toSystemDependentName(outputPath);
      }
    }
    return null;
  }

  @Nonnull
  @Override
  public String getPresentableName() {
    return myPointer.getName();
  }

  @Override
  public Image getIcon() {
    final Artifact artifact = getArtifact();
    return artifact != null ? artifact.getArtifactType().getIcon() : null;
  }

  @Override
  public boolean isValid() {
    return getArtifact() != null;
  }

  @Override
  public boolean isArchive() {
    Artifact artifact = getArtifact();
    return artifact != null && !(artifact.getRootElement() instanceof ArtifactRootElement<?>);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ArtifactDeploymentSourceImpl)) return false;

    return myPointer.equals(((ArtifactDeploymentSourceImpl)o).myPointer);

  }

  @Override
  public int hashCode() {
    return myPointer.hashCode();
  }

  @Nonnull
  @Override
  public DeploymentSourceType<?> getType() {
    return DeploymentSourceType.EP_NAME.findExtension(ArtifactDeploymentSourceType.class);
  }
}
