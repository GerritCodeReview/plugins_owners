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

package com.googlesource.gerrit.owners;

import static com.google.gerrit.server.project.ProjectCache.noSuchProject;
import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.owners.common.PluginSettings;
import java.util.Optional;

@Singleton
public class OwnersSubmitRule implements SubmitRule {
  public static class CodeOwnerSubmitRuleModule extends AbstractModule {
    @Override
    public void configure() {
      bind(SubmitRule.class)
          .annotatedWith(Exports.named("OwnersSubmitRule"))
          .to(OwnersSubmitRule.class);
    }
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PluginSettings pluginSettings;
  private final ProjectCache projectCache;

  @Inject
  OwnersSubmitRule(PluginSettings pluginSettings, ProjectCache projectCache) {
    this.pluginSettings = pluginSettings;
    this.projectCache = projectCache;
  }

  @Override
  public Optional<SubmitRecord> evaluate(ChangeData cd) {
    if (!pluginSettings.enableSubmitRule()) {
      logger.atInfo().log("Submit rule is disabled therefore it will not be evaluated.");
      return Optional.empty();
    }

    requireNonNull(cd, "changeData");

    if (cd.change().isClosed()) {
      logger.atInfo().log("Change is closed therefore OWNERS file based rules are skipped.");
      return Optional.empty();
    }

    try {
      ProjectState projectState =
          projectCache.get(cd.project()).orElseThrow(noSuchProject(cd.project()));
      if (projectState.hasPrologRules()) {
        logger.atInfo().log(
            "Project has prolog rules enabled. It may interfere with submit rule evaluation.");
      }

      logger.atInfo().log("TODO: evaluate OWNERS file based rules if available");
    } catch (NoSuchProjectException e) {
      throw new IllegalStateException("Unable to find project while evaluating owners rule", e);
    }

    return Optional.empty();
  }
}
