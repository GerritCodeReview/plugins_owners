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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.owners.api.OwnersAttentionSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ReviewerManager {
  private static final Logger log = LoggerFactory.getLogger(ReviewerManager.class);

  private final OneOffRequestContext requestContext;
  private final GerritApi gApi;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeData.Factory changeDataFactory;
  private final PermissionBackend permissionBackend;

  /**
   * TODO: The optional injection here is needed for keeping backward compatibility with existing
   * setups that do not have the owners-api.jar configured as Gerrit libModule.
   *
   * <p>Once merged to master, the optional injection can go and this can be moved as extra argument
   * in the constructor.
   */
  @Inject(optional = true)
  private DynamicItem<OwnersAttentionSet> ownersForAttentionSet;

  private final AutoassignConfig cfg;

  @Inject
  public ReviewerManager(
      OneOffRequestContext requestContext,
      GerritApi gApi,
      IdentifiedUser.GenericFactory userFactory,
      ChangeData.Factory changeDataFactory,
      PermissionBackend permissionBackend,
      AutoassignConfig cfg) {
    this.requestContext = requestContext;
    this.gApi = gApi;
    this.userFactory = userFactory;
    this.changeDataFactory = changeDataFactory;
    this.permissionBackend = permissionBackend;
    this.cfg = cfg;
  }

  public void addReviewers(
      NameKey projectNameKey, ChangeApi cApi, Collection<Account.Id> accountsIds)
      throws ReviewerManagerException, NoSuchProjectException {
    try {
      ChangeInfo changeInfo = cApi.get();
      Set<Integer> currentReviewers =
          cApi.reviewers().stream().map(ri -> ri._accountId).collect(Collectors.toSet());
      ReviewerState reviewerState = cfg.autoassignedReviewerState(projectNameKey);
      try (ManualRequestContext ctx =
          requestContext.openAs(Account.id(changeInfo.owner._accountId))) {
        // TODO(davido): Switch back to using changes API again,
        // when it supports batch mode for adding reviewers
        ReviewInput in = new ReviewInput();
        in.reviewers = new ArrayList<>(accountsIds.size());
        Collection<Account.Id> validOwnersForAttentionSet = new ArrayList<>(accountsIds.size());
        for (Account.Id account : accountsIds) {
          if (!currentReviewers.contains(account.get()) && isVisibleTo(changeInfo, account)) {
            AddReviewerInput addReviewerInput = new AddReviewerInput();
            addReviewerInput.reviewer = account.toString();
            addReviewerInput.state = reviewerState;
            in.reviewers.add(addReviewerInput);

            if (reviewerState == ReviewerState.REVIEWER) {
              validOwnersForAttentionSet.add(account);
            }
          } else {
            log.warn(
                "Not adding account {} as reviewer to change {} because the associated ref is not visible",
                account,
                changeInfo._number);
          }
        }

        Collection<Account.Id> reviewersAccounts;
        if (validOwnersForAttentionSet.isEmpty()) {
          reviewersAccounts = Collections.emptyList();
        } else {
          reviewersAccounts =
              Optional.ofNullable(ownersForAttentionSet)
                  .map(DynamicItem::get)
                  .filter(Objects::nonNull)
                  .map(owners -> owners.addToAttentionSet(changeInfo, validOwnersForAttentionSet))
                  .orElse(validOwnersForAttentionSet);
        }

        in.ignoreAutomaticAttentionSetRules = true;
        in.addToAttentionSet =
            reviewersAccounts.stream()
                .map(
                    (reviewer) ->
                        new AttentionSetInput(
                            reviewer.toString(), "Selected as member of the OWNERS file"))
                .collect(Collectors.toList());

        gApi.changes().id(changeInfo.id).current().review(in);
      }
    } catch (RestApiException e) {
      log.error("Couldn't add reviewers to the change", e);
      throw new ReviewerManagerException(e);
    }
  }

  private boolean isVisibleTo(ChangeInfo changeInfo, Account.Id account) {
    ChangeData changeData =
        changeDataFactory.create(
            Project.nameKey(changeInfo.project), Change.id(changeInfo._number));
    return permissionBackend
        .user(userFactory.create(account))
        .change(changeData)
        .testOrFalse(ChangePermission.READ);
  }
}
