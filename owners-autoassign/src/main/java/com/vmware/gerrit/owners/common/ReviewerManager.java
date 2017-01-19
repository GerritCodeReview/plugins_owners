/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners.common;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

@Singleton
public class ReviewerManager {
  private static final Logger log = LoggerFactory
      .getLogger(ReviewerManager.class);

  private final GerritApi gApi;
  private final OneOffRequestContext requestContext;

  @Inject
  public ReviewerManager(GerritApi gApi, OneOffRequestContext requestContext) {
    this.gApi = gApi;
    this.requestContext = requestContext;
  }

  public void addReviewers(Change change, Collection<Account.Id> reviewers)
      throws ReviewerManagerException {
    try (ManualRequestContext ctx = requestContext.openAs(change.getOwner())) {

      ChangeApi cApi = gApi.changes().id(change.getId().get());
      for (Account.Id account : reviewers) {
        cApi.addReviewer(account.toString());
      }

    } catch (RestApiException | OrmException e) {
      log.error("Couldn't add reviewers to the change", e);
      throw new ReviewerManagerException(e);
    }
  }
}
