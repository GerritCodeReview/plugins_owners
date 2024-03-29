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

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.api.projects.ProjectConfigEntryType;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.inject.AbstractModule;
import java.util.Arrays;

public class AutoassignConfigModule extends AbstractModule {
  public static final String PROJECT_CONFIG_AUTOASSIGN_WIP_CHANGES = "autoAssignWip";
  public static final String PROJECT_CONFIG_AUTOASSIGN_FIELD = "autoAssignField";

  @Override
  protected void configure() {
    bind(ProjectConfigEntry.class)
        .annotatedWith(Exports.named(PROJECT_CONFIG_AUTOASSIGN_WIP_CHANGES))
        .toInstance(
            new ProjectConfigEntry(
                "Auto-assign WIP changes", InheritableBoolean.INHERIT, InheritableBoolean.class));
    bind(ProjectConfigEntry.class)
        .annotatedWith(Exports.named(PROJECT_CONFIG_AUTOASSIGN_FIELD))
        .toInstance(
            new ProjectConfigEntry(
                "Auto-assign field",
                ReviewerState.REVIEWER.name(),
                ProjectConfigEntryType.LIST,
                Arrays.asList(ReviewerState.CC.name(), ReviewerState.REVIEWER.name()),
                true,
                "Change field to use for the assigned accounts"));
  }
}
