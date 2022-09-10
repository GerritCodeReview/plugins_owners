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
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.owners.common.Accounts;
import com.googlesource.gerrit.owners.common.PathOwners;
import com.googlesource.gerrit.owners.common.PluginSettings;
import com.googlesource.gerrit.owners.entities.Owner;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class GetFilesOwners implements RestReadView<RevisionResource> {
  private static final Logger log = LoggerFactory.getLogger(GetFilesOwners.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final PatchListCache patchListCache;
  private final Accounts accounts;
  private final AccountCache accountCache;
  private final GitRepositoryManager repositoryManager;
  private final PluginSettings pluginSettings;

  @Inject GerritApi gApi;

  @Inject
  GetFilesOwners(
      PatchListCache patchListCache,
      Accounts accounts,
      AccountCache accountCache,
      GitRepositoryManager repositoryManager,
      PluginSettings pluginSettings) {
    this.patchListCache = patchListCache;
    this.accounts = accounts;
    this.accountCache = accountCache;
    this.repositoryManager = repositoryManager;
    this.pluginSettings = pluginSettings;
  }

  @Override
  public Response<?> apply(RevisionResource revision)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    PatchSet ps = revision.getPatchSet();
    Change change = revision.getChange();
    int id = revision.getChangeResource().getChange().getChangeId();

    try (Repository repository = repositoryManager.openRepository(change.getProject())) {
      PatchList patchList = patchListCache.get(change, ps);

      String branch = change.getDest().branch();
      PathOwners owners =
          new PathOwners(
              accounts,
              repository,
              pluginSettings.isBranchDisabled(branch) ? Optional.empty() : Optional.of(branch),
              patchList);

      ChangeInfo ci = gApi.changes().id(id).get(EnumSet.of(ListChangesOption.DETAILED_LABELS));
      Map<String, LabelInfo> allLabels = ci.labels;
      List<ApprovalInfo> allApprovals = allLabels.get("Code-Review").all;
      Map<Integer, ApprovalInfo> approvedIdsToVote =
          Maps.uniqueIndex(allApprovals, x -> x._accountId);

      Map<String, Set<Owner>> fileToOwners =
          Maps.transformValues(
              owners.getFileOwners(),
              ids ->
                  ids.stream()
                      .map(o -> getOwnerFromAccountId(o, approvedIdsToVote))
                      .collect(Collectors.toSet()));

      return Response.ok(fileToOwners);
    }
  }

  private Owner getOwnerFromAccountId(
      Account.Id accountId, Map<Integer, ApprovalInfo> approvedIdsToVote) {
    AccountState accountState = accountCache.getEvenIfMissing(accountId);
    ApprovalInfo approvalInfo = approvedIdsToVote.get(accountId.get());
    Map<String, Integer> labelToVote =
        new HashMap<String, Integer>() {
          {
            put("Code-Review", approvalInfo.value);
          }
        };
    List<Map<String, Integer>> labelToVotes = new ArrayList<Map<String, Integer>>();
    labelToVotes.add(labelToVote);
    return new Owner(
        accountState.account().fullName(), accountState.account().id().get(), labelToVotes);
  }
}
