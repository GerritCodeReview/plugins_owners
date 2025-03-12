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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.*;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
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
import com.googlesource.gerrit.owners.common.*;
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
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class GetFilesOwners implements RestReadView<RevisionResource> {
  private final Accounts accounts;
  private final AccountCache accountCache;
  private final ProjectCache projectCache;
  private final GitRepositoryManager repositoryManager;
  private final PluginSettings pluginSettings;
  private final GerritApi gerritApi;
  private final PathOwnersEntriesCache cache;

  static final String MISSING_CODE_REVIEW_LABEL =
      "Cannot calculate file owners state when review label is not configured";
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  GetFilesOwners(
      Accounts accounts,
      AccountCache accountCache,
      ProjectCache projectCache,
      GitRepositoryManager repositoryManager,
      PluginSettings pluginSettings,
      GerritApi gerritApi,
      PathOwnersEntriesCache cache) {
    this.accounts = accounts;
    this.accountCache = accountCache;
    this.projectCache = projectCache;
    this.repositoryManager = repositoryManager;
    this.pluginSettings = pluginSettings;
    this.gerritApi = gerritApi;
    this.cache = cache;
  }

  @Override
  public Response<FilesOwnersResponse> apply(RevisionResource revision)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    Change change = revision.getChange();
    ChangeData changeData = revision.getChangeResource().getChangeData();

    Project.NameKey project = change.getProject();
    List<Project.NameKey> projectParents =
        projectCache.get(project).map(PathOwners::getParents).orElse(Collections.emptyList());

    try (Repository repository = repositoryManager.openRepository(project)) {
      Set<String> changePaths = new HashSet<>(changeData.currentFilePaths());

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
              cache,
              pluginSettings.globalLabel());

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

      Map<Integer, Map<String, Integer>> ownersLabels = getLabels(change.getChangeId());

      LabelDefinition label = getLabelDefinition(owners, changeData);

      Map<String, Set<GroupOwner>> filesWithPendingOwners =
          Maps.filterEntries(
              fileToOwners,
              (fileOwnerEntry) ->
                  !isApprovedByOwner(
                      fileExpandedOwners.get(fileOwnerEntry.getKey()), ownersLabels, label));

      Map<String, Set<GroupOwner>> filesApprovedByOwners =
              Maps.filterEntries(
                      fileToOwners,
                      (fileOwnerEntry) ->
                              isApprovedByOwner(
                                      fileExpandedOwners.get(fileOwnerEntry.getKey()), ownersLabels, label));

      return Response.ok(new FilesOwnersResponse(ownersLabels, filesWithPendingOwners, filesApprovedByOwners));
    } catch (InvalidOwnersFileException e) {
      logger.atSevere().withCause(e).log("Reading/parsing OWNERS file error.");
      throw new ResourceConflictException(e.getMessage(), e);
    }
  }

  private LabelDefinition getLabelDefinition(PathOwners owners, ChangeData changeData) throws ResourceNotFoundException {
      LabelDefinition labelDefinition = Optional.of(pluginSettings.enableSubmitRequirement())
          .filter(Boolean::booleanValue)
          .flatMap(enabled -> getLabelFromOwners(owners, changeData))
          .orElse(LabelDefinition.CODE_REVIEW);
      if (changeData
        .getLabelTypes()
        .byLabel(labelDefinition.getLabelType().getLabelId()).isPresent()) {
        return labelDefinition;
      } else {
        LabelNotFoundException labelNotFoundException = new LabelNotFoundException(changeData.project(), labelDefinition.getLabelType().getLabelId().get());
        logger.atInfo().withCause(labelNotFoundException).log("Invalid configuration");
        throw new ResourceNotFoundException(MISSING_CODE_REVIEW_LABEL, labelNotFoundException);
      }
  }

  private Optional<LabelDefinition> getLabelFromOwners(PathOwners owners, ChangeData changeData)
      throws LabelNotFoundException {
    return owners
        .getLabel()
        .map(
            label ->
                new LabelDefinition(
                    label.getLabelType(),
                    label.getScore()));
  }

  private boolean isApprovedByOwner(
      Set<GroupOwner> fileOwners,
      Map<Integer, Map<String, Integer>> ownersLabels,
      LabelDefinition label) {
    return fileOwners.stream()
        .filter(owner -> owner instanceof Owner)
        .map(owner -> ((Owner) owner).getId())
        .flatMap(ownerId -> codeReviewLabelValue(ownersLabels, ownerId, label.getLabelType().getName()))
        .anyMatch(value -> value >= label.getScore());
  }

  private Stream<Integer> codeReviewLabelValue(
      Map<Integer, Map<String, Integer>> ownersLabels, int ownerId, String labelId) {
    return Stream.ofNullable(ownersLabels.get(ownerId))
        .flatMap(m -> Stream.ofNullable(m.get(labelId)));
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

  static class LabelNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    LabelNotFoundException(Project.NameKey project, String labelId) {
      super(String.format("Project %s has no %s label defined", project, labelId));
    }
  }
}
