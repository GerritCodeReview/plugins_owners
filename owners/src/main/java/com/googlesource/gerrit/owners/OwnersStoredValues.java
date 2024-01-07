// Copyright (c) 2013 VMware, Inc. All Rights Reserved.
// Copyright (C) 2017 The Android Open Source Project
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

package com.googlesource.gerrit.owners;

import com.google.gerrit.entities.Project;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.rules.prolog.StoredValue;
import com.google.gerrit.server.rules.prolog.StoredValues;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlesource.gerrit.owners.common.Accounts;
import com.googlesource.gerrit.owners.common.InvalidOwnersFileException;
import com.googlesource.gerrit.owners.common.PathOwners;
import com.googlesource.gerrit.owners.common.PathOwnersEntriesCache;
import com.googlesource.gerrit.owners.common.PluginSettings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** StoredValues for the Gerrit OWNERS plugin. */
public class OwnersStoredValues {
  private static final Logger log = LoggerFactory.getLogger(OwnersStoredValues.class);

  public static StoredValue<PathOwners> PATH_OWNERS;

  public static synchronized void initialize(
      Accounts accounts,
      PluginSettings settings,
      PathOwnersEntriesCache cache,
      OwnersMetrics metrics) {
    if (PATH_OWNERS != null) {
      return;
    }
    log.info("Initializing OwnerStoredValues");
    PATH_OWNERS =
        new StoredValue<>() {
          @Override
          protected PathOwners createValue(Prolog engine) {
            Map<String, FileDiffOutput> patchList = StoredValues.DIFF_LIST.get(engine);
            Repository repository = StoredValues.REPOSITORY.get(engine);
            ProjectState projectState = StoredValues.PROJECT_STATE.get(engine);
            GitRepositoryManager gitRepositoryManager = StoredValues.REPO_MANAGER.get(engine);

            metrics.countConfigLoads.increment();
            try (Timer0.Context ctx = metrics.loadConfig.start()) {
              List<Project.NameKey> parentProjectsNameKeys = PathOwners.getParents(projectState);
              String branch = StoredValues.getChange(engine).getDest().branch();
              return new PathOwners(
                  accounts,
                  gitRepositoryManager,
                  repository,
                  parentProjectsNameKeys,
                  settings.isBranchDisabled(branch) ? Optional.empty() : Optional.of(branch),
                  patchList,
                  settings.expandGroups(),
                  projectState.getName(),
                  cache);
            } catch (InvalidOwnersFileException e) {
              // re-throw exception as it is already logged but more importantly it is nicely
              // handled by the prolog rules evaluator and results in prolog rule error
              throw new IllegalStateException(e);
            }
          }
        };
  }

  private OwnersStoredValues() {}
}
