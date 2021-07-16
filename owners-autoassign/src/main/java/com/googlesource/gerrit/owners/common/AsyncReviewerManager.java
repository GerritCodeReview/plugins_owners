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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AsyncReviewerManager implements ReviewerManager {

  private static final Logger log = LoggerFactory.getLogger(AsyncReviewerManager.class);

  private final SyncReviewerManager syncReviewerManager;
  private final AutoAssignConfig config;
  private final ScheduledExecutorService executor;
  private final OneOffRequestContext requestContext;

  class AddReviewersTask implements Runnable {
    private final ChangeApi cApi;
    private final Collection<Id> reviewers;
    private final Supplier<Integer> changeNum;
    private int retryNum;

    public AddReviewersTask(ChangeApi cApi, Collection<Account.Id> reviewers) {
      this.cApi = cApi;
      this.changeNum =
          Suppliers.memoize(
              () -> {
                try {
                  return cApi.get()._number;
                } catch (RestApiException e) {
                  log.error("Unable to access change number", e);
                  return -1;
                }
              });
      this.reviewers = reviewers;
    }

    @Override
    public String toString() {
      return "auto-assign reviewers to change "
          + changeNum.get()
          + (retryNum > 0 ? "(retry #" + retryNum + ")" : "");
    }

    @Override
    public void run() {
      try (ManualRequestContext ctx = requestContext.open()) {
        syncReviewerManager.addReviewers(cApi, reviewers);
      } catch (Exception e) {
        retryNum++;

        if (retryNum > config.retryCount()) {
          log.error("{} *FAILED* after {} tries", this, retryNum, e);
        } else {
          long retryInterval = config.retryInterval();
          log.warn("{} *FAILED*: retry #{} after {} msec", this, retryNum, retryInterval, e);
          executor.schedule(this, retryInterval, TimeUnit.MILLISECONDS);
        }
      }
    }
  }

  @Inject
  public AsyncReviewerManager(
      AutoAssignConfig config,
      WorkQueue executorFactory,
      SyncReviewerManager syncReviewerManager,
      OneOffRequestContext requestContext) {
    this.config = config;
    this.syncReviewerManager = syncReviewerManager;
    this.executor = executorFactory.createQueue(config.asyncThreads(), "AsyncReviewerManager");
    this.requestContext = requestContext;
  }

  @Override
  public void addReviewers(ChangeApi cApi, Collection<Account.Id> reviewers)
      throws ReviewerManagerException {
    executor.schedule(
        new AddReviewersTask(cApi, reviewers), config.asyncDelay(), TimeUnit.MILLISECONDS);
  }
}
