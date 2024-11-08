// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners.common;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.config.PluginConfigFactory;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PluginSettingsTest {
  private static final String PLUGIN_NAME = "plugin-name";
  @Mock PluginConfigFactory mockPluginConfigFactory;

  private PluginSettings pluginSettings;

  public void setupMocks(Config pluginConfig) {
    when(mockPluginConfigFactory.getGlobalPluginConfig(PLUGIN_NAME)).thenReturn(pluginConfig);
    pluginSettings = new PluginSettings(mockPluginConfigFactory, PLUGIN_NAME);
  }

  @Test
  public void allBranchesAreEnabledByDefault() {
    setupMocks(new Config());

    assertThat(pluginSettings.disabledBranchPatterns()).isEmpty();
    assertThat(pluginSettings.isBranchDisabled("some-branch")).isFalse();
  }

  @Test
  public void branchRefShouldBeDisabled() {
    String branchName = "refs/heads/some-branch";
    Config pluginConfig = new Config();
    pluginConfig.setString("owners", "disable", "branch", branchName);
    setupMocks(pluginConfig);

    assertThat(pluginSettings.disabledBranchPatterns()).contains(branchName);
    assertThat(pluginSettings.isBranchDisabled(branchName)).isTrue();
  }

  @Test
  public void branchNameShouldBeDisabled() {
    String branchName = "some-branch";
    String branchRefName = Constants.R_HEADS + branchName;
    Config pluginConfig = new Config();
    pluginConfig.setString("owners", "disable", "branch", branchRefName);
    setupMocks(pluginConfig);

    assertThat(pluginSettings.disabledBranchPatterns()).contains(branchRefName);
    assertThat(pluginSettings.isBranchDisabled(branchName)).isTrue();
  }

  @Test
  public void branchNameShouldBeDisabledByRegex() {
    String branchName1 = "some-branch-1";
    String branchName2 = "some-branch-2";
    String branchRefRegex = Constants.R_HEADS + "some-branch-\\d";
    Config pluginConfig = new Config();
    pluginConfig.setString("owners", "disable", "branch", branchRefRegex);
    setupMocks(pluginConfig);

    assertThat(pluginSettings.disabledBranchPatterns()).contains(branchRefRegex);
    assertThat(pluginSettings.isBranchDisabled(branchName1)).isTrue();
    assertThat(pluginSettings.isBranchDisabled(branchName2)).isTrue();
  }

  @Test
  public void globalLabelIsEmptyByDefault() {
    setupMocks(new Config());

    assertThat(pluginSettings.globalLabel()).isEqualTo(Optional.empty());
  }

  @Test
  public void globalLabelSetByConfig() {
    LabelDefinition globalLabelName = new LabelDefinition("Custom-Label", (short) 2);
    Config pluginConfig = new Config();
    pluginConfig.setString("owners", null, "label", "Custom-Label,2");
    setupMocks(pluginConfig);

    assertThat(pluginSettings.globalLabel()).isEqualTo(Optional.of(globalLabelName));
  }
}
