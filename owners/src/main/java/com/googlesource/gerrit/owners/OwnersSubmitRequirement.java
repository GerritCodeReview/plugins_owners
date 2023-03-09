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
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelFunction;
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
import com.googlesource.gerrit.owners.common.ScoreDefinition;
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

    Change change = cd.change();
    if (change.isClosed()) {
      logger.atInfo().log("Change is closed therefore OWNERS file based requirement is skipped.");
      return Optional.empty();
    }

    Project.NameKey project = cd.project();
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    if (projectState.hasPrologRules()) {
      logger.atInfo().atMostEvery(1, TimeUnit.DAYS).log(
          "Project has prolog rules enabled. It may interfere with submit requirement evaluation.");
    }

    String branch = change.getDest().branch();
    List<Project.NameKey> parents =
        Optional.ofNullable(projectState.getProject().getParent())
            .map(Arrays::asList)
            .orElse(Collections.emptyList());

    try (Repository repo = repoManager.openRepository(project)) {
      PathOwners pathOwners =
          new PathOwners(
              accounts,
              repoManager,
              repo,
              parents,
              pluginSettings.isBranchDisabled(branch) ? Optional.empty() : Optional.of(branch),
              getDiff(project, cd.currentPatchSet().commitId()),
              pluginSettings.expandGroups());

      Map<String, Set<Id>> fileOwners = pathOwners.getFileOwners();
      if (fileOwners.isEmpty()) {
        logger.atInfo().log("Change has no file owners defined. Skipping submit requirement.");
        return Optional.empty();
      }

      ChangeNotes notes = cd.notes();
      requireNonNull(notes, "notes");
      LabelTypes labelTypes = projectState.getLabelTypes(notes);
      ScoreDefinition label = resolveLabel(labelTypes, pathOwners.getLabel());
      LabelAndScore ownersLabel = ownersLabel(labelTypes, label, project);
      Account.Id uploader = notes.getCurrentPatchSet().uploader();
      Map<Account.Id, List<PatchSetApproval>> approvalsByAccount =
          Streams.stream(approvalsUtil.byPatchSet(notes, cd.currentPatchSet().id()))
              .collect(Collectors.groupingBy(PatchSetApproval::accountId));

      Set<String> missingApprovals =
          fileOwners.entrySet().stream()
              .filter(
                  requiredApproval ->
                      isApprovalMissing(
                          requiredApproval, uploader, approvalsByAccount, ownersLabel))
              .map(Map.Entry::getKey)
              .collect(toSet());

      return Optional.of(
          missingApprovals.isEmpty()
              ? ok()
              : notReady(
                  label.getLabel(),
                  String.format(
                      "Missing approvals for path(s): [%s]",
                      Joiner.on(", ").join(missingApprovals))));
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("TODO: handle exceptions");
      throw new IllegalStateException(
          "Unable to open repository while evaluating owners requirement", e);
    } catch (DiffNotAvailableException e) {
      logger.atSevere().withCause(e).log("TODO: handle exceptions");
      throw new IllegalStateException("Unable to get diff while evaluating owners requirement", e);
    }
  }

  /**
   * The idea is to select the label type that is configured for owner to cast the vote. If nothing
   * is configured in the OWNERS file then `Code-Review` will be selected.
   *
   * @param labelTypes labels configured for project
   * @param label and score definition that is configured in the OWNERS file
   */
  static ScoreDefinition resolveLabel(LabelTypes labelTypes, Optional<ScoreDefinition> label) {
    return label.orElse(ScoreDefinition.CODE_REVIEW);
  }

  /**
   * Create {@link LabelAndScore} definition with a single label LabelTypes if label can be found or
   * empty otherwise. Note that score definition copied from OWNERS.
   *
   * @param labelTypes labels configured for project
   * @param label and score definition (optional) that is resolved from the OWNERS file
   * @param project that change is evaluated for
   */
  static LabelAndScore ownersLabel(
      LabelTypes labelTypes, ScoreDefinition label, Project.NameKey project) {
    return labelTypes
        .byLabel(label.getLabel())
        .map(
            type ->
                new LabelAndScore(
                    new LabelTypes(Collections.singletonList(type)), label.getScore()))
        .orElseGet(
            () -> {
              logger.atSevere().log(
                  "OWNERS label '%s' is not configured for '%s' project. Change is not submittable.",
                  label, project);
              return LabelAndScore.EMPTY;
            });
  }

  static boolean isApprovalMissing(
      Map.Entry<String, Set<Account.Id>> requiredApproval,
      Account.Id uploader,
      Map<Account.Id, List<PatchSetApproval>> approvalsByAccount,
      LabelAndScore ownersLabel) {
    return requiredApproval.getValue().stream()
        .noneMatch(
            fileOwner -> isApprovedByOwner(fileOwner, uploader, approvalsByAccount, ownersLabel));
  }

  static boolean isApprovedByOwner(
      Account.Id fileOwner,
      Account.Id uploader,
      Map<Account.Id, List<PatchSetApproval>> approvalsByAccount,
      LabelAndScore ownersLabel) {
    return Optional.ofNullable(approvalsByAccount.get(fileOwner))
        .map(
            approvals ->
                approvals.stream()
                    .anyMatch(
                        approval ->
                            hasSufficientApproval(approval, ownersLabel, fileOwner, uploader)))
        .orElse(false);
  }

  static boolean hasSufficientApproval(
      PatchSetApproval approval,
      LabelAndScore ownersLabel,
      Account.Id fileOwner,
      Account.Id uploader) {
    return ownersLabel
        .getOwnersLabelType()
        .byLabel(approval.labelId())
        .map(label -> isLabelApproved(label, ownersLabel.getScore(), fileOwner, uploader, approval))
        .orElse(false);
  }

  static boolean isLabelApproved(
      LabelType label,
      Optional<Integer> score,
      Account.Id fileOwner,
      Account.Id uploader,
      PatchSetApproval approval) {
    if (label.isIgnoreSelfApproval() && fileOwner.equals(uploader)) {
      return false;
    }

    return score
        .map(value -> approval.value() >= value)
        .orElseGet(
            () -> {
              LabelFunction function = label.getFunction();
              if (function.isMaxValueRequired()) {
                return label.isMaxPositive(approval);
              }

              if (function.isBlock() && label.isMaxNegative(approval)) {
                return false;
              }

              return approval.value() > label.getDefaultValue();
            });
  }

  static class LabelAndScore {
    static LabelAndScore EMPTY =
        new LabelAndScore(new LabelTypes(Collections.emptyList()), Optional.empty());

    private final LabelTypes ownersLabelType;
    private final Optional<Integer> score;

    LabelAndScore(LabelTypes ownersLabelType, Optional<Integer> score) {
      this.ownersLabelType = ownersLabelType;
      this.score = score;
    }

    LabelTypes getOwnersLabelType() {
      return ownersLabelType;
    }

    Optional<Integer> getScore() {
      return score;
    }
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

  private static SubmitRecord notReady(String ownersLabel, String missingApprovals) {
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.status = SubmitRecord.Status.NOT_READY;
    submitRecord.errorMessage = missingApprovals;
    submitRecord.requirements = List.of(SUBMIT_REQUIREMENT);
    SubmitRecord.Label label = new SubmitRecord.Label();
    label.label = String.format("%s from owners", ownersLabel);
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
