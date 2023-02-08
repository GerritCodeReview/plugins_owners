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
import com.google.gerrit.entities.Account.Id;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class SyncReviewerManager implements ReviewerManager {
  private static final Logger log = LoggerFactory.getLogger(SyncReviewerManager.class);

  private final OneOffRequestContext requestContext;
  private final GerritApi gApi;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeData.Factory changeDataFactory;
  private final PermissionBackend permissionBackend;

  @Inject
  public SyncReviewerManager(
      OneOffRequestContext requestContext,
      GerritApi gApi,
      IdentifiedUser.GenericFactory userFactory,
      ChangeData.Factory changeDataFactory,
      PermissionBackend permissionBackend) {
    this.requestContext = requestContext;
    this.gApi = gApi;
    this.userFactory = userFactory;
    this.changeDataFactory = changeDataFactory;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public void addReviewers(
      Project.NameKey projectName, ChangeApi cApi, Collection<Account.Id> reviewers)
      throws ReviewerManagerException {
    try {
      ChangeInfo changeInfo = cApi.get();
      try (ManualRequestContext ctx =
          requestContext.openAs(Account.id(changeInfo.owner._accountId))) {
        // TODO(davido): Switch back to using changes API again,
        // when it supports batch mode for adding reviewers
        ReviewInput in = new ReviewInput();
        in.reviewers = new ArrayList<>(reviewers.size());
        for (Account.Id account : reviewers) {
          if (isVisibleTo(changeInfo, account)) {
            AddReviewerInput addReviewerInput = new AddReviewerInput();
            addReviewerInput.reviewer = account.toString();
            in.reviewers.add(addReviewerInput);
          } else {
            log.warn(
                "Not adding account {} as reviewer to change {} because the associated ref is not visible",
                account,
                changeInfo._number);
          }
        }
        gApi.changes().id(changeInfo.id).current().review(in);
      }
    } catch (RestApiException e) {
      log.error("Couldn't add reviewers to the change", e);
      throw new ReviewerManagerException(e);
    }
  }

  private boolean isVisibleTo(ChangeInfo changeInfo, Id account) {
    ChangeData changeData =
        changeDataFactory.create(
            Project.nameKey(changeInfo.project), Change.id(changeInfo._number));
    return permissionBackend
        .user(userFactory.create(account))
        .change(changeData)
        .testOrFalse(ChangePermission.READ);
  }
}
