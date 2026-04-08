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

import static com.googlesource.gerrit.owners.AutoOwnersApprovalFunctions.allowsAutoApprovalOnPatch;
import static com.googlesource.gerrit.owners.AutoOwnersApprovalFunctions.modifiedFilesBetweenPatchSets;
import static com.googlesource.gerrit.owners.AutoOwnersApprovalFunctions.touchedPaths;

import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
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
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.owners.common.Accounts;
import com.googlesource.gerrit.owners.common.InvalidOwnersFileException;
import com.googlesource.gerrit.owners.common.PathOwners;
import com.googlesource.gerrit.owners.common.PathOwnersEntriesCache;
import com.googlesource.gerrit.owners.common.PluginSettings;
import com.googlesource.gerrit.owners.entities.FilesOwnersResponse;
import com.googlesource.gerrit.owners.entities.GroupOwner;
import com.googlesource.gerrit.owners.entities.Owner;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class GetFilesOwners implements RestReadView<RevisionResource> {
  private final Accounts accounts;
  private final AccountCache accountCache;
  private final ProjectCache projectCache;
  private final GitRepositoryManager repositoryManager;
  private final DiffOperations diffOperations;
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
      DiffOperations diffOperations,
      PluginSettings pluginSettings,
      GerritApi gerritApi,
      PathOwnersEntriesCache cache) {
    this.accounts = accounts;
    this.accountCache = accountCache;
    this.projectCache = projectCache;
    this.repositoryManager = repositoryManager;
    this.diffOperations = diffOperations;
    this.pluginSettings = pluginSettings;
    this.gerritApi = gerritApi;
    this.cache = cache;
  }

  public boolean isAnyFileOwnedBy(
      Account.Id owner, Set<String> changePaths, Project.NameKey project, String branch)
      throws IOException, InvalidOwnersFileException {
    return !filterFilesOwnedBy(owner, changePaths, project, branch).isEmpty();
  }

  public Set<String> filterFilesOwnedBy(
      Account.Id owner, Set<String> changePaths, Project.NameKey project, String branch)
      throws IOException, InvalidOwnersFileException {
    PathOwners owners = getPathOwners(project, branch, changePaths);
    Map<String, Set<Account.Id>> filesWithOwner = owners.getFileOwners();

    return changePaths.stream()
        .filter(filePath -> filesWithOwner.getOrDefault(filePath, Set.of()).contains(owner))
        .collect(Collectors.toSet());
  }

  public boolean allOwnedFilesAllowAutoApproval(
      Set<String> ownedPaths, Project.NameKey project, String branch)
      throws IOException, InvalidOwnersFileException {
    PathOwners owners = getPathOwners(project, branch, ownedPaths);
    Set<String> filesAllowedForOwnersAutoApproval = owners.getFileOwnersAllowedAutoApproval();

    return filesAllowedForOwnersAutoApproval.containsAll(ownedPaths);
  }

  @Override
  public Response<FilesOwnersResponse> apply(RevisionResource revision)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    Change change = revision.getChange();
    ChangeData changeData = revision.getChangeResource().getChangeData();

    Project.NameKey project = change.getProject();
    try {
      Set<String> changePaths = new HashSet<>(changeData.currentFilePaths());

      String branch = change.getDest().branch();
      PathOwners owners = getPathOwners(project, branch, changePaths);

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

      LabelAndScore label = getLabelDefinition(owners, changeData);

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

      Set<String> filesAutoApproved =
          getFilesAutoApproved(revision, changeData, filesApprovedByOwners);

      Map<String, Set<GroupOwner>> filesAutoApprovedByOwners =
          Maps.filterKeys(filesApprovedByOwners, filesAutoApproved::contains);

      Map<String, Set<GroupOwner>> filesExplicitlyApprovedByOwners =
          Maps.filterKeys(filesApprovedByOwners, filePath -> !filesAutoApproved.contains(filePath));

      return Response.ok(
          new FilesOwnersResponse(
              ownersLabels,
              filesWithPendingOwners,
              filesExplicitlyApprovedByOwners,
              filesAutoApprovedByOwners));
    } catch (InvalidOwnersFileException e) {
      logger.atSevere().withCause(e).log("Reading/parsing OWNERS file error.");
      throw new ResourceConflictException(e.getMessage(), e);
    }
  }

  private PathOwners getPathOwners(Project.NameKey project, String branch, Set<String> changePaths)
      throws InvalidOwnersFileException, IOException {
    List<Project.NameKey> projectParents =
        projectCache.get(project).map(PathOwners::getParents).orElse(Collections.emptyList());
    try (Repository repository = repositoryManager.openRepository(project)) {
      return new PathOwners(
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
    }
  }

  private LabelAndScore getLabelDefinition(PathOwners owners, ChangeData changeData)
      throws ResourceNotFoundException {

    try {
      return getLabelFromOwners(owners, changeData)
          .orElseGet(
              () ->
                  new LabelAndScore(
                      LabelId.CODE_REVIEW, getMaxScoreForLabel(changeData, LabelId.CODE_REVIEW)));
    } catch (LabelNotFoundException e) {
      logger.atInfo().withCause(e).log("Invalid configuration");
      throw new ResourceNotFoundException(MISSING_CODE_REVIEW_LABEL, e);
    }
  }

  private Optional<LabelAndScore> getLabelFromOwners(PathOwners owners, ChangeData changeData)
      throws LabelNotFoundException {
    return owners
        .getLabel()
        .map(
            label ->
                new LabelAndScore(
                    label.getName(),
                    label
                        .getScore()
                        .orElseGet(() -> getMaxScoreForLabel(changeData, label.getName()))));
  }

  private short getMaxScoreForLabel(ChangeData changeData, String labelId)
      throws LabelNotFoundException {
    return changeData
        .getLabelTypes()
        .byLabel(labelId)
        .map(label -> label.getMaxPositive())
        .orElseThrow(() -> new LabelNotFoundException(changeData.change().getProject(), labelId));
  }

  private boolean isApprovedByOwner(
      Set<GroupOwner> fileOwners,
      Map<Integer, Map<String, Integer>> ownersLabels,
      LabelAndScore label) {
    return fileOwners.stream()
        .filter(owner -> owner instanceof Owner)
        .map(owner -> ((Owner) owner).getId())
        .flatMap(ownerId -> codeReviewLabelValue(ownersLabels, ownerId, label.getLabelId()))
        .anyMatch(value -> value >= label.getScore());
  }

  private Set<String> getFilesAutoApproved(
      RevisionResource revision,
      ChangeData changeData,
      Map<String, Set<GroupOwner>> filesApprovedByOwners)
      throws IOException, InvalidOwnersFileException, DiffNotAvailableException {
    PatchSet sourcePatchSet = getPreviousPatchSet(revision);
    if (sourcePatchSet == null) {
      return Set.of();
    }
    Account.Id ownerId = revision.getChange().getOwner();
    String branch = changeData.change().getDest().branch();
    Project.NameKey project = changeData.project();

    Set<String> allFilesTouchedInTheLastPatchSet =
        touchedPaths(
            modifiedFilesBetweenPatchSets(
                diffOperations, project, sourcePatchSet, revision.getPatchSet()));

    Map<Account.Id, List<PatchSetApproval>> approvalsByAccount =
        changeData.currentApprovals().stream()
            .collect(Collectors.groupingBy(PatchSetApproval::accountId));

    List<PatchSetApproval> changeOwnerApprovals = approvalsByAccount.get(ownerId);

    // If the change owner didn't approve the label or the label was not copied from the previous
    // patch set then auto-owners-approvals cannot qualify for this patch-set.
    if (changeOwnerApprovals == null
        || changeOwnerApprovals.stream().anyMatch(isNotCopiedApproval())) {
      return Set.of();
    }

    // Otherwise we check if the change owner was eligible for auto-owners-approved
    Set<String> filesOwnedByChangeOwnerInTheLastPatchSet =
        filterFilesOwnedBy(ownerId, allFilesTouchedInTheLastPatchSet, project, branch);

    if (!allowsAutoApprovalOnPatch(
        ownerId,
        ownerId,
        revision.getPatchSet().uploader(),
        filesOwnedByChangeOwnerInTheLastPatchSet,
        allFilesTouchedInTheLastPatchSet,
        this,
        project,
        branch)) {
      return Set.of();
    }

    return getAutoApprovedFiles(
        filesOwnedByChangeOwnerInTheLastPatchSet, filesApprovedByOwners, approvalsByAccount);
  }

  private Set<String> getAutoApprovedFiles(
      Set<String> filesApprovedByChangeOwner,
      Map<String, Set<GroupOwner>> fileExpandedOwners,
      Map<Account.Id, List<PatchSetApproval>> currentApprovalsByAccount) {
    return filesApprovedByChangeOwner.stream()
        .filter(notExplicitlyApprovedByAnOwner(fileExpandedOwners, currentApprovalsByAccount))
        .collect(Collectors.toSet());
  }

  private Predicate<String> notExplicitlyApprovedByAnOwner(
      Map<String, Set<GroupOwner>> fileExpandedOwners,
      Map<Account.Id, List<PatchSetApproval>> currentApprovalsByAccount) {
    return filePath ->
        ownerIds(fileExpandedOwners.get(filePath))
            .map(currentApprovalsByAccount::get)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .noneMatch(isNotCopiedApproval());
  }

  private PatchSet getPreviousPatchSet(RevisionResource revision) {
    int sourcePatchSetNumber = revision.getPatchSet().id().get() - 1;
    if (sourcePatchSetNumber < 1) {
      return null;
    }

    return revision
        .getNotes()
        .getPatchSets()
        .get(PatchSet.id(revision.getChange().getId(), sourcePatchSetNumber));
  }

  private static Predicate<PatchSetApproval> isNotCopiedApproval() {
    Predicate<PatchSetApproval> func = PatchSetApproval::copied;
    return func.negate();
  }

  private Stream<Account.Id> ownerIds(Set<GroupOwner> fileOwners) {
    return fileOwners.stream()
        .filter(owner -> owner instanceof Owner)
        .map(owner -> Account.id(((Owner) owner).getId()));
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

  private static class LabelAndScore {
    private final String labelId;
    private final short score;

    private LabelAndScore(String labelId, short score) {
      this.labelId = labelId;
      this.score = score;
    }

    private String getLabelId() {
      return labelId;
    }

    private short getScore() {
      return score;
    }
  }
}
