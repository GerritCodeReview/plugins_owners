// Copyright (C) 2021 The Android Open Source Project
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

import static com.google.gerrit.extensions.client.InheritableBoolean.TRUE;
import static com.googlesource.gerrit.owners.common.AutoassignConfigModule.PROJECT_CONFIG_AUTOASSIGN_FIELD;
import static com.googlesource.gerrit.owners.common.AutoassignConfigModule.PROJECT_CONFIG_AUTOASSIGN_WIP_CHANGES;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class AutoassignConfig {
  private final PluginSettings config;

  @Inject
  AutoassignConfig(PluginSettings config) {
    this.config = config;
  }

  public boolean autoAssignWip(Project.NameKey projectKey) throws NoSuchProjectException {
    return config
        .projectSpecificConfig(projectKey)
        .getEnum(PROJECT_CONFIG_AUTOASSIGN_WIP_CHANGES, TRUE)
        .equals(TRUE);
  }

  public ReviewerState autoassignedReviewerState(Project.NameKey projectKey)
      throws NoSuchProjectException {
    return config
        .projectSpecificConfig(projectKey)
        .getEnum(PROJECT_CONFIG_AUTOASSIGN_FIELD, ReviewerState.REVIEWER);
  }

  public boolean isBranchDisabled(String branch) {
    return config.isBranchDisabled(branch);
  }

  public boolean expandGroups() {
    return config.expandGroups();
  }
}
