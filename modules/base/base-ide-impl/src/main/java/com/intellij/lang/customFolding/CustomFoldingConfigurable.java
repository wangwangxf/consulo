package com.intellij.lang.customFolding;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import consulo.project.Project;
import org.jetbrains.annotations.Nls;
import javax.annotation.Nonnull;

import javax.swing.*;

/**
 * @author Rustam Vishnyakov
 */
public class CustomFoldingConfigurable implements SearchableConfigurable {

  private CustomFoldingConfiguration myConfiguration;
  private CustomFoldingSettingsPanel mySettingsPanel;

  public CustomFoldingConfigurable(Project project) {
    myConfiguration = CustomFoldingConfiguration.getInstance(project);
    mySettingsPanel = new CustomFoldingSettingsPanel();
  }

  @Nonnull
  @Override
  public String getId() {
    return getDisplayName();
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Custom Folding"; //TODO<rv> Move to resources
  }

  @Override
  public JComponent createComponent() {
    return mySettingsPanel.getComponent();
  }

  @Override
  public boolean isModified() {
    return myConfiguration.getState().isEnabled() != mySettingsPanel.isEnabled() ||
           !myConfiguration.getState().getStartFoldingPattern().equals(mySettingsPanel.getStartPattern()) ||
           !myConfiguration.getState().getEndFoldingPattern().equals(mySettingsPanel.getEndPattern()) ||
           !myConfiguration.getState().getDefaultCollapsedStatePattern().equals(mySettingsPanel.getCollapsedStatePattern());
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfiguration.getState().setStartFoldingPattern(mySettingsPanel.getStartPattern());
    myConfiguration.getState().setEndFoldingPattern(mySettingsPanel.getEndPattern());
    myConfiguration.getState().setEnabled(mySettingsPanel.isEnabled());
    myConfiguration.getState().setDefaultCollapsedStatePattern(mySettingsPanel.getCollapsedStatePattern());
  }

  @Override
  public void reset() {
    mySettingsPanel.setStartPattern(myConfiguration.getState().getStartFoldingPattern());
    mySettingsPanel.setEndPattern(myConfiguration.getState().getEndFoldingPattern());
    mySettingsPanel.setEnabled(myConfiguration.getState().isEnabled());
    mySettingsPanel.setCollapsedStatePattern(myConfiguration.getState().getDefaultCollapsedStatePattern());
  }

  @Override
  public void disposeUIResources() {
  }
}
