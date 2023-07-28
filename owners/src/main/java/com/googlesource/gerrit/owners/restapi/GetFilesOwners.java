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
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
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
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.owners.common.Accounts;
import com.googlesource.gerrit.owners.common.PathOwners;
import com.googlesource.gerrit.owners.common.PathOwnersEntriesCache;
import com.googlesource.gerrit.owners.common.PluginSettings;
import com.googlesource.gerrit.owners.entities.FilesOwnersResponse;
import com.googlesource.gerrit.owners.entities.GroupOwner;
import com.googlesource.gerrit.owners.entities.Owner;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class GetFilesOwners implements RestReadView<RevisionResource> {
  private final Accounts accounts;
  private final AccountCache accountCache;
  private final ProjectCache projectCache;
  private final GitRepositoryManager repositoryManager;
  private final PluginSettings pluginSettings;
  private final GerritApi gerritApi;
  private final ChangeData.Factory changeDataFactory;
  private final PathOwnersEntriesCache cache;

  @Inject
  GetFilesOwners(
      Accounts accounts,
      AccountCache accountCache,
      ProjectCache projectCache,
      GitRepositoryManager repositoryManager,
      PluginSettings pluginSettings,
      GerritApi gerritApi,
      ChangeData.Factory changeDataFactory,
      PathOwnersEntriesCache cache) {
    this.accounts = accounts;
    this.accountCache = accountCache;
    this.projectCache = projectCache;
    this.repositoryManager = repositoryManager;
    this.pluginSettings = pluginSettings;
    this.gerritApi = gerritApi;
    this.changeDataFactory = changeDataFactory;
    this.cache = cache;
  }

  @Override
  public Response<FilesOwnersResponse> apply(RevisionResource revision)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    Change change = revision.getChange();
    Optional<Short> codeReviewMaxValue =
        revision
            .getChangeResource()
            .getChangeData()
            .getLabelTypes()
            .byLabel(LabelId.CODE_REVIEW)
            .map(LabelType::getMaxPositive);
    int id = revision.getChangeResource().getChange().getChangeId();

    Project.NameKey project = change.getProject();
    List<Project.NameKey> projectParents =
        projectCache.get(project).map(PathOwners::getParents).orElse(Collections.emptyList());

    try (Repository repository = repositoryManager.openRepository(project)) {
      Set<String> changePaths = new HashSet<>(changeDataFactory.create(change).currentFilePaths());

      String branch = change.getDest().branch();
      PathOwners owners =
          new PathOwners(
              accounts,
              repositoryManager,
              repository,
              projectParents,
              pluginSettings.isBranchDisabled(branch) ? Optional.empty() : Optional.of(branch),
              changePaths,
              pluginSettings.expandGroups(),
              project.get(),
              cache);

      Map<String, Set<GroupOwner>> fileExpandedOwners =
          Maps.transformValues(
              owners.getFileOwners(),
              ids ->
                  ids.stream()
                      .map(this::getOwnerFromAccountId)
                      .flatMap(Optional::stream)
                      .collect(Collectors.toSet()));

      Map<String, Set<GroupOwner>> fileToOwners =
          pluginSettings.expandGroups()
              ? fileExpandedOwners
              : Maps.transformValues(
                  owners.getFileGroupOwners(),
                  groupNames ->
                      groupNames.stream().map(GroupOwner::new).collect(Collectors.toSet()));

      Map<Integer, Map<String, Integer>> ownersLabels = getLabels(id);

      Map<String, Set<GroupOwner>> filesWithPendingOwners =
          codeReviewMaxValue
              .map(
                  value ->
                      Maps.filterEntries(
                          fileToOwners,
                          (fileOwnerEntry) ->
                              !isApprovedByOwner(
                                  fileExpandedOwners.get(fileOwnerEntry.getKey()),
                                  ownersLabels,
                                  value)))
              .orElse(fileToOwners);

      return Response.ok(new FilesOwnersResponse(ownersLabels, filesWithPendingOwners));
    }
  }

  private boolean isApprovedByOwner(
      Set<GroupOwner> fileOwners,
      Map<Integer, Map<String, Integer>> ownersLabels,
      short codeReviewMaxValue) {
    return fileOwners.stream()
        .filter(owner -> owner instanceof Owner)
        .map(owner -> ((Owner) owner).getId())
        .map(ownerId -> codeReviewLabelValue(ownersLabels, ownerId))
        .anyMatch(value -> value.filter(v -> v == codeReviewMaxValue).isPresent());
  }

  private Optional<Integer> codeReviewLabelValue(
      Map<Integer, Map<String, Integer>> ownersLabels, int ownerId) {
    return Optional.ofNullable(ownersLabels.get(ownerId))
        .flatMap(m -> Optional.ofNullable(m.get(LabelId.CODE_REVIEW)));
  }

  /**
   * This method returns ta Map representing the "owners_labels" object of the response. When
   * serialized the Map, has to to return the following JSON: the following JSON:
   *
   * <pre>
   * {
   *   "1000001" : {
   *    "Code-Review" : 1,
   *    "Verified" : 0
   *   },
   *   "1000003" : {
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
        (label, labelInfo) -> {
          Optional.ofNullable(labelInfo.all)
              .ifPresent(
                  approvalInfos -> {
                    approvalInfos.forEach(
                        approvalInfo -> {
                          int currentOwnerId = approvalInfo._accountId;
                          Map<String, Integer> currentOwnerLabels =
                              ownerToLabels.getOrDefault(currentOwnerId, new HashMap<>());
                          currentOwnerLabels.put(label, approvalInfo.value);
                          ownerToLabels.put(currentOwnerId, currentOwnerLabels);
                        });
                  });
        });

    return ownerToLabels;
  }

  private Optional<Owner> getOwnerFromAccountId(Account.Id accountId) {
    return accountCache
        .get(accountId)
        .map(as -> new Owner(as.account().fullName(), as.account().id().get()));
  }
}
