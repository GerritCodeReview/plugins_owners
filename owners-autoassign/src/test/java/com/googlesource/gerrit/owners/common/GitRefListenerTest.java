// Copyright (C) 2019 The Android Open Source Project
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

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.Repository;
import org.junit.Ignore;

@Ignore
public class GitRefListenerTest extends GitRefListener {
  int processedEvents = 0;

  @Inject
  public GitRefListenerTest(
      GerritApi api,
      PatchListCache patchListCache,
      GitRepositoryManager repositoryManager,
      Accounts accounts,
      PluginConfigFactory configFactory,
      SyncReviewerManager reviewerManager,
      OneOffRequestContext oneOffReqCtx,
      Provider<CurrentUser> currentUserProvider) {
    super(
        api,
        patchListCache,
        repositoryManager,
        accounts,
        configFactory,
        reviewerManager,
        oneOffReqCtx,
        currentUserProvider);
  }

  @Override
  public void processEvent(Repository allprojrepository, Repository repository, Event event) {
    processedEvents++;
  }

  int getProcessedEvents() {
    return processedEvents;
  }
}
