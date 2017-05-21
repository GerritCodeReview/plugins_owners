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

package com.vmware.gerrit.owners.common;

import static com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace.IGNORE_NONE;

import java.io.IOException;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

@Listen
public class GitRefListener implements GitReferenceUpdatedListener {
  private static final Logger logger =
      LoggerFactory.getLogger(GitRefListener.class);

  private static final String CHANGES_REF = "refs/changes/";

  private final Provider<ReviewDb> db;

  private final PatchListCache patchListCache;

  private final GitRepositoryManager repositoryManager;

  private final Accounts accounts;

  private final ReviewerManager reviewerManager;

  @Inject
  public GitRefListener(Provider<ReviewDb> db,
      PatchListCache patchListCache,
      GitRepositoryManager repositoryManager,
      Accounts accounts,
      ReviewerManager reviewerManager) {
    this.db = db;
    this.patchListCache = patchListCache;
    this.repositoryManager = repositoryManager;
    this.accounts = accounts;
    this.reviewerManager = reviewerManager;
  }

  @Override
  public void onGitReferenceUpdated(Event event) {
    String projectName = event.getProjectName();
    Repository repository;
    try {
      repository = repositoryManager
          .openRepository(Project.NameKey.parse(projectName));
      try {
        processEvent(repository, event);
      } finally {
        repository.close();
      }
    } catch (IOException e) {
      logger.warn("Couldn't open repository: {}", projectName, e);
    }
  }

  private void processEvent(Repository repository, Event event) {
    if (event.getRefName().startsWith(CHANGES_REF)) {
      Change.Id id = Change.Id.fromRef(event.getRefName());
      ReviewDb reviewDb = db.get();
      // The provider injected by Gerrit is shared with other workers on the
      // same local thread and thus cannot be closed in this event listener.
      try {
        Change change = reviewDb.changes().get(id);
        PatchList patchList = getPatchList(event, change);
        if (patchList != null) {
          PathOwners owners =
              new PathOwners(accounts, repository, change.getDest().get(), patchList);
          Set<Account.Id> allReviewers = Sets.newHashSet();
          allReviewers.addAll(owners.get().values());
          for(Matcher matcher: owners.getMatchers().values()) {
            allReviewers.addAll( matcher.getOwners());
          }
          logger.debug("Autoassigned reviewers are: {}",allReviewers.toString());
          reviewerManager.addReviewers(change, allReviewers);
        }
      } catch (OrmException e) {
        logger.warn("Could not open change: {}", id, e);
      } catch (ReviewerManagerException e) {
        logger.warn("Could not add reviewers for change: {}", id, e);
      }
    }
  }

  private PatchList getPatchList(Event event, Change change) {
    ObjectId newId = null;
    if (event.getNewObjectId() != null) {
      newId = ObjectId.fromString(event.getNewObjectId());
    }

    PatchListKey plKey = new PatchListKey(null, newId, IGNORE_NONE);
    try {
      return patchListCache.get(plKey, change.getProject());
    } catch (PatchListNotAvailableException e) {
      logger.warn("Could not load patch list: {}", plKey, e);
    }
    return null;
  }
}
