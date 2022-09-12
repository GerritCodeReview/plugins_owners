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

import com.google.common.collect.Maps;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
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
import com.googlesource.gerrit.owners.entities.FilesOwnersResponse;
import com.googlesource.gerrit.owners.entities.Owner;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class GetFilesOwners implements RestReadView<RevisionResource> {

  private final PatchListCache patchListCache;
  private final Accounts accounts;
  private final AccountCache accountCache;
  private final GitRepositoryManager repositoryManager;
  private final PluginSettings pluginSettings;
  private final GerritApi gerritApi;

  @Inject
  GetFilesOwners(
      PatchListCache patchListCache,
      Accounts accounts,
      AccountCache accountCache,
      GitRepositoryManager repositoryManager,
      PluginSettings pluginSettings,
      GerritApi gerritApi) {
    this.patchListCache = patchListCache;
    this.accounts = accounts;
    this.accountCache = accountCache;
    this.repositoryManager = repositoryManager;
    this.pluginSettings = pluginSettings;
    this.gerritApi = gerritApi;
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

      Map<String, Set<Owner>> fileToOwners =
          Maps.transformValues(
              owners.getFileOwners(),
              ids ->
                  ids.stream()
                      .map(this::getOwnerFromAccountId)
                      .flatMap(Optional::stream)
                      .collect(Collectors.toSet()));

      return Response.ok(new FilesOwnersResponse(getLabels(id), fileToOwners));
    }
  }

  /**
   * This method returns the "labels" object of the response. It has to build the objets to return
   * the following JSON:
   *
   * <pre>
   * {
   *   10001 : {
   *    "Code-Review" : 1,
   *    "Verified" : 0
   *   },
   *   10003 : {
   *    "Code-Review" : 2,
   *    "Verified" : 1
   *  }
   * }
   *
   * </pre>
   */
  private Map<Integer, Map<String, Integer>> getLabels(int id) throws RestApiException {
    ChangeInfo changeInfo =
        gerritApi.changes().id(id).get(EnumSet.of(ListChangesOption.DETAILED_LABELS));

    Map<Integer, Map<String, Integer>> ownerToLabels = new HashMap<>();

    changeInfo.labels.forEach(
        (label, approvalInfos) -> {
          if (approvalInfos.all != null) {
            approvalInfos.all.forEach(
                approvalInfo -> {
                  int currentOwnerId = approvalInfo._accountId;
                  Map<String, Integer> currentOwnerLabels =
                      ownerToLabels.getOrDefault(currentOwnerId, new HashMap<>());
                  currentOwnerLabels.put(label, approvalInfo.value);
                  ownerToLabels.put(currentOwnerId, currentOwnerLabels);
                });
          }
        });

    return ownerToLabels;
  }

  private Optional<Owner> getOwnerFromAccountId(Account.Id accountId) {
    Optional<AccountState> maybeAccountState = accountCache.get(accountId);
    return maybeAccountState.map(as -> new Owner(as.account().fullName(), as.account().id().get()));
  }
}
