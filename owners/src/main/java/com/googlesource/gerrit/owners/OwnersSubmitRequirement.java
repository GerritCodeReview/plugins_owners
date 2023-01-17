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
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners;

import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

import com.google.common.base.Joiner;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Account.Id;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.LegacySubmitRequirement;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.owners.common.Accounts;
import com.googlesource.gerrit.owners.common.PathOwners;
import com.googlesource.gerrit.owners.common.PluginSettings;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class OwnersSubmitRequirement implements SubmitRule {
  public static class OwnersSubmitRequirementModule extends AbstractModule {
    @Override
    public void configure() {
      bind(SubmitRule.class)
          .annotatedWith(Exports.named("OwnersSubmitRequirement"))
          .to(OwnersSubmitRequirement.class);
    }
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final LegacySubmitRequirement SUBMIT_REQUIREMENT =
      LegacySubmitRequirement.builder().setFallbackText("Owners").setType("owners").build();

  private final PluginSettings pluginSettings;
  private final ProjectCache projectCache;
  private final Accounts accounts;
  private final GitRepositoryManager repoManager;
  private final DiffOperations diffOperations;
  private final ApprovalsUtil approvalsUtil;

  @Inject
  OwnersSubmitRequirement(
      PluginSettings pluginSettings,
      ProjectCache projectCache,
      Accounts accounts,
      GitRepositoryManager repoManager,
      DiffOperations diffOperations,
      ApprovalsUtil approvalsUtil) {
    this.pluginSettings = pluginSettings;
    this.projectCache = projectCache;
    this.accounts = accounts;
    this.repoManager = repoManager;
    this.diffOperations = diffOperations;
    this.approvalsUtil = approvalsUtil;
  }

  @Override
  public Optional<SubmitRecord> evaluate(ChangeData cd) {
    requireNonNull(cd, "changeData");

    if (cd.change().isClosed()) {
      logger.atInfo().log("Change is closed therefore OWNERS file based rules are skipped.");
      return Optional.empty();
    }

    ProjectState projectState =
        projectCache.get(cd.project()).orElseThrow(illegalState(cd.project()));
    if (projectState.hasPrologRules()) {
      logger.atInfo().atMostEvery(1, TimeUnit.DAYS).log(
          "Project has prolog rules enabled. It may interfere with submit rule evaluation.");
    }

    String branch = cd.change().getDest().branch();
    List<Project.NameKey> parents =
        Optional.ofNullable(projectState.getProject().getParent())
            .map(Arrays::asList)
            .orElse(Collections.emptyList());

    try (Repository repo = repoManager.openRepository(cd.project())) {
      PathOwners pathOwners =
          new PathOwners(
              accounts,
              repoManager,
              repo,
              parents,
              pluginSettings.isBranchDisabled(branch) ? Optional.empty() : Optional.of(branch),
              getDiff(cd.project(), cd.currentPatchSet().commitId()),
              pluginSettings.expandGroups());

      Map<String, Set<Id>> fileOwners = pathOwners.getFileOwners();
      if (fileOwners.isEmpty()) {
        logger.atInfo().log("Change has no file owners defined. Skipping rules.");
        return Optional.empty();
      }

      ChangeNotes notes = cd.notes();
      requireNonNull(notes, "notes");
      LabelTypes labelTypes = ownersLabel(projectState.getLabelTypes(notes));
      Account.Id uploader = notes.getCurrentPatchSet().uploader();
      Map<Account.Id, List<PatchSetApproval>> approvalsByAccount =
          Streams.stream(approvalsUtil.byPatchSet(notes, cd.currentPatchSet().id()))
              .collect(Collectors.groupingBy(PatchSetApproval::accountId));

      Set<String> missingApprovals =
          fileOwners.entrySet().stream()
              .filter(
                  requiredApproval ->
                      isApprovalMissing(requiredApproval, uploader, approvalsByAccount, labelTypes))
              .map(Map.Entry::getKey)
              .collect(toSet());

      return Optional.of(
          missingApprovals.isEmpty()
              ? ok()
              : notReady(
                  String.format(
                      "Missing approvals for path(s): [%s]",
                      Joiner.on(", ").join(missingApprovals))));
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("TODO: handle exceptions");
      throw new IllegalStateException("Unable to open repository while evaluating owners rule", e);
    } catch (DiffNotAvailableException e) {
      logger.atSevere().withCause(e).log("TODO: handle exceptions");
      throw new IllegalStateException("Unable to get diff while evaluating owners rule", e);
    }
  }

  /** the idea is to select the label type that is configured for owner to cast the vote */
  private LabelTypes ownersLabel(LabelTypes labelTypes) {

    // TODO: there is no specific label configuration introduced to the OWNERS file therefore for
    // the time being set it to Code Review explicitly or nothing if it doesn't exist for the
    // project in question
    return new LabelTypes(
        labelTypes
            .byLabel(LabelId.CODE_REVIEW)
            .map(Collections::singletonList)
            .orElseGet(Collections::emptyList));
  }

  static boolean isApprovalMissing(
      Map.Entry<String, Set<Account.Id>> requiredApproval,
      Account.Id uploader,
      Map<Account.Id, List<PatchSetApproval>> approvalsByAccount,
      LabelTypes labelTypes) {
    return requiredApproval.getValue().stream()
        .anyMatch(
            fileOwner -> isNotApprovedByOwner(fileOwner, uploader, approvalsByAccount, labelTypes));
  }

  static boolean isNotApprovedByOwner(
      Account.Id fileOwner,
      Account.Id uploader,
      Map<Account.Id, List<PatchSetApproval>> approvalsByAccount,
      LabelTypes labelTypes) {
    return Optional.ofNullable(approvalsByAccount.get(fileOwner))
        .map(
            approvals ->
                approvals.stream()
                    .noneMatch(
                        approval ->
                            hasSufficientApproval(approval, labelTypes, fileOwner, uploader)))
        .orElse(true);
  }

  static boolean hasSufficientApproval(
      PatchSetApproval approval, LabelTypes labelTypes, Account.Id fileOwner, Account.Id uploader) {
    return labelTypes
        .byLabel(approval.labelId())
        .map(label -> isLabelApproved(label, fileOwner, uploader, approval))
        .orElse(false);
  }

  static boolean isLabelApproved(
      LabelType label, Account.Id fileOwner, Account.Id uploader, PatchSetApproval approval) {
    if (label.isIgnoreSelfApproval() && fileOwner.equals(uploader)) {
      return false;
    }

    LabelFunction function = label.getFunction();
    if (function.isMaxValueRequired()) {
      return label.isMaxPositive(approval);
    }

    if (function.isBlock() && label.isMaxNegative(approval)) {
      return false;
    }

    if (function.isRequired()) {
      return approval.value() > label.getDefaultValue();
    }

    return true;
  }

  private Map<String, FileDiffOutput> getDiff(Project.NameKey project, ObjectId revision)
      throws DiffNotAvailableException {
    requireNonNull(project, "project");
    requireNonNull(revision, "revision");

    // Use parentNum=0 to do the comparison against the default base.
    // For non-merge commits the default base is the only parent (aka parent 1, initial commits
    // are not supported).
    // For merge commits the default base is the auto-merge commit which should be used as base IOW
    // only the changes from it should be reviewed as changes against the parent 1 were already
    // reviewed
    return diffOperations.listModifiedFilesAgainstParent(project, revision, 0);
  }

  private static SubmitRecord notReady(String missingApprovals) {
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.status = SubmitRecord.Status.NOT_READY;
    submitRecord.errorMessage = missingApprovals;
    submitRecord.requirements = List.of(SUBMIT_REQUIREMENT);
    SubmitRecord.Label label = new SubmitRecord.Label();
    label.label = "Code-Review from owners";
    label.status = SubmitRecord.Label.Status.NEED;
    submitRecord.labels = List.of(label);
    return submitRecord;
  }

  private static SubmitRecord ok() {
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.status = SubmitRecord.Status.OK;
    submitRecord.requirements = List.of(SUBMIT_REQUIREMENT);
    return submitRecord;
  }
}
