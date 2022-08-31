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
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners.common;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

/** Global owners plugin's settings defined globally or on a per-project basis. */
@Singleton
public class PluginSettings {
  private final ImmutableSet<String> disabledBranchesPatterns;
  private final PluginConfigFactory configFactory;
  private final String ownersPluginName;
  private final Config globalPluginConfig;

  @Inject
  public PluginSettings(PluginConfigFactory configFactory, @PluginName String ownersPluginName) {
    this.configFactory = configFactory;
    this.ownersPluginName = ownersPluginName;

    this.globalPluginConfig = configFactory.getGlobalPluginConfig(ownersPluginName);
    disabledBranchesPatterns =
        ImmutableSet.copyOf(globalPluginConfig.getStringList("owners", "disable", "branch"));
  }

  /**
   * Branches that should be ignored for the OWNERS processing.
   *
   * @return set of branches regex
   */
  public ImmutableSet<String> disabledBranchesPatterns() {
    return disabledBranchesPatterns;
  }

  /**
   * Project-specific config of the owners plugin.
   *
   * @param projectKey project name
   * @return project-specific plugin config
   * @throws NoSuchProjectException if the project cannot be found
   */
  public PluginConfig projectSpecificConfig(Project.NameKey projectKey)
      throws NoSuchProjectException {
    return configFactory.getFromProjectConfigWithInheritance(projectKey, ownersPluginName);
  }
}
