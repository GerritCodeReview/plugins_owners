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
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.owners.common.Accounts;
import com.googlesource.gerrit.owners.common.PathOwners;
import com.googlesource.gerrit.owners.common.PluginSettings;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

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
  private final Accounts accounts;
  private final GitRepositoryManager repoManager;
  private final DiffOperations diffOperations;

  @Inject
  OwnersSubmitRule(
      PluginSettings pluginSettings,
      ProjectCache projectCache,
      Accounts accounts,
      GitRepositoryManager repoManager,
      DiffOperations diffOperations) {
    this.pluginSettings = pluginSettings;
    this.projectCache = projectCache;
    this.accounts = accounts;
    this.repoManager = repoManager;
    this.diffOperations = diffOperations;
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

      String branch = cd.change().getDest().branch();
      List<Project.NameKey> parents =
          Optional.ofNullable(projectState.getProject().getParent())
              .map(Arrays::asList)
              .orElse(Collections.emptyList());

      try (Repository repo = repoManager.openRepository(cd.project())) {
        PathOwners pathOwners =
            new PathOwners(
                accounts,
                repoManager,
                repo,
                parents,
                pluginSettings.isBranchDisabled(branch) ? Optional.empty() : Optional.of(branch),
                getDiff(cd.project(), cd.currentPatchSet().commitId()),
                pluginSettings.expandGroups());

        if (pathOwners.getFileOwners().isEmpty()) {
          logger.atInfo().log("Change has no file owners defined. Skipping rules.");
          return Optional.empty();
        }

        logger.atInfo().log("TODO: check if change is approved.");
      } catch (IOException | DiffNotAvailableException e) {
        logger.atSevere().withCause(e).log("Opening repository failed");
      }
    } catch (NoSuchProjectException e) {
      logger.atInfo().log("TODO: handle exceptions");
      throw new IllegalStateException("Unable to find project while evaluating owners rule", e);
    }

    return Optional.empty();
  }

  private Map<String, FileDiffOutput> getDiff(Project.NameKey project, ObjectId revision)
      throws DiffNotAvailableException {
    requireNonNull(project, "project");
    requireNonNull(revision, "revision");

    // Use parentNum=0 to do the comparison against the default base.
    // For non-merge commits the default base is the only parent (aka parent 1, initial commits
    // are not supported).
    // For merge commits the default base is the auto-merge commit which should be used as base IOW
    // only the changes from it should be reviewed as changes against the parent 1 were already
    // reviewed
    return diffOperations.listModifiedFilesAgainstParent(project, revision, 0);
  }
}
