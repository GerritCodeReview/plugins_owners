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

package com.googlesource.gerrit.owners.common;

import static com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace.IGNORE_NONE;

import com.google.common.base.Predicates;
import com.google.common.collect.Sets;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Listen
public class GitRefListener implements GitReferenceUpdatedListener {
  private static final Logger logger = LoggerFactory.getLogger(GitRefListener.class);

  private final GerritApi api;

  private final PatchListCache patchListCache;
  private final GitRepositoryManager repositoryManager;
  private final Accounts accounts;
  private final ReviewerManager reviewerManager;

  private final OneOffRequestContext oneOffReqCtx;

  private Provider<CurrentUser> currentUserProvider;

  private ChangeNotes.Factory notesFactory;

  @Inject
  public GitRefListener(
      GerritApi api,
      PatchListCache patchListCache,
      GitRepositoryManager repositoryManager,
      Accounts accounts,
      ReviewerManager reviewerManager,
      OneOffRequestContext oneOffReqCtx,
      Provider<CurrentUser> currentUserProvider,
      ChangeNotes.Factory notesFactory) {
    this.api = api;
    this.patchListCache = patchListCache;
    this.repositoryManager = repositoryManager;
    this.accounts = accounts;
    this.reviewerManager = reviewerManager;
    this.oneOffReqCtx = oneOffReqCtx;
    this.currentUserProvider = currentUserProvider;
    this.notesFactory = notesFactory;
  }

  @Override
  public void onGitReferenceUpdated(Event event) {
    if (event.isDelete()) {
      logger.debug("Ref-update event on ref %s is a deletion: ignoring", event.getRefName());
      return;
    }

    AccountInfo updaterAccountInfo = event.getUpdater();
    CurrentUser currentUser = currentUserProvider.get();
    if (currentUser.isIdentifiedUser()) {
      handleGitReferenceUpdated(event);
    } else if (updaterAccountInfo != null) {
      handleGitReferenceUpdatedAsUser(event, Account.id(updaterAccountInfo._accountId));
    } else {
      handleGitReferenceUpdatedAsServer(event);
    }
  }

  private void handleGitReferenceUpdatedAsUser(Event event, Account.Id updaterAccountId) {
    try (ManualRequestContext ctx = oneOffReqCtx.openAs(updaterAccountId)) {
      handleGitReferenceUpdated(event);
    } catch (StorageException e) {
      logger.warn("Unable to process event {} on project {}", event, event.getProjectName(), e);
    }
  }

  private void handleGitReferenceUpdatedAsServer(Event event) {
    try (ManualRequestContext ctx = oneOffReqCtx.open()) {
      handleGitReferenceUpdated(event);
    } catch (StorageException e) {
      logger.warn("Unable to process event {} on project {}", event, event.getProjectName(), e);
    }
  }

  private void handleGitReferenceUpdated(Event event) {
    String projectName = event.getProjectName();
    Repository repository;
    try {
      NameKey projectNameKey = Project.NameKey.parse(projectName);
      repository = repositoryManager.openRepository(projectNameKey);
      try {
        String refName = event.getRefName();
        Change.Id changeId = Change.Id.fromRef(refName);
        if (changeId != null
            && (!RefNames.isNoteDbMetaRef(refName)
                || isChangeSetReadyForReview(projectNameKey, changeId, event.getNewObjectId()))) {
          processEvent(repository, event, changeId);
        }
      } finally {
        repository.close();
      }
    } catch (IOException e) {
      logger.warn("Couldn't open repository: {}", projectName, e);
    }
  }

  private boolean isChangeSetReadyForReview(
      NameKey project, Change.Id changeId, String metaObjectId) {
    ChangeNotes changeNotes = notesFactory.createChecked(project, changeId);
    return !changeNotes.getChange().isWorkInProgress()
        && changeNotes.getChangeMessages().stream()
            .filter(message -> message.getKey().uuid().equals(metaObjectId))
            .map(message -> message.getTag())
            .filter(Predicates.notNull())
            .anyMatch(tag -> tag.contains(ChangeMessagesUtil.TAG_SET_READY));
  }

  public void processEvent(Repository repository, Event event, Change.Id cId) {
    Changes changes = api.changes();
    // The provider injected by Gerrit is shared with other workers on the
    // same local thread and thus cannot be closed in this event listener.
    try {
      ChangeApi cApi = changes.id(cId.get());
      ChangeInfo change = cApi.get();
      PatchList patchList = getPatchList(repository, event, change);
      if (patchList != null) {
        PathOwners owners = new PathOwners(accounts, repository, change.branch, patchList);
        Set<Account.Id> allReviewers = Sets.newHashSet();
        allReviewers.addAll(owners.get().values());
        allReviewers.addAll(owners.getReviewers().values());
        for (Matcher matcher : owners.getMatchers().values()) {
          allReviewers.addAll(matcher.getOwners());
          allReviewers.addAll(matcher.getReviewers());
        }
        logger.debug("Autoassigned reviewers are: {}", allReviewers.toString());
        reviewerManager.addReviewers(cApi, allReviewers);
      }
    } catch (RestApiException e) {
      logger.warn("Could not open change: {}", cId, e);
    } catch (ReviewerManagerException e) {
      logger.warn("Could not add reviewers for change: {}", cId, e);
    }
  }

  private PatchList getPatchList(Repository repository, Event event, ChangeInfo change) {
    ObjectId newId = null;
    PatchListKey plKey;
    try {
      if (RefNames.isNoteDbMetaRef(event.getRefName())) {
        newId = ObjectId.fromString(change.currentRevision);
        RevCommit revCommit = repository.parseCommit(newId);
        plKey = PatchListKey.againstBase(newId, revCommit.getParentCount());
      } else {
        if (event.getNewObjectId() != null) {
          newId = ObjectId.fromString(event.getNewObjectId());
        }
        plKey = PatchListKey.againstCommit(null, newId, IGNORE_NONE);
      }
      return patchListCache.get(plKey, Project.nameKey(change.project));
    } catch (PatchListNotAvailableException | IOException e) {
      logger.warn("Could not load patch list for change {}", change.id, e);
    }
    return null;
  }
}
