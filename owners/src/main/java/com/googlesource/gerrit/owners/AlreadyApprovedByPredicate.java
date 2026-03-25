// Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.flogger.LazyArgs.lazy;
import static com.googlesource.gerrit.owners.AlreadyApprovedByOperand.FULL_OPERAND_WITH_PLUGIN_NAME;
import static com.googlesource.gerrit.owners.AlreadyApprovedByOperand.OPERAND;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.gerrit.server.query.approval.ApprovalContext;
import com.google.gerrit.server.query.approval.UserInPredicate;
import com.googlesource.gerrit.owners.common.InvalidOwnersFileException;
import com.googlesource.gerrit.owners.restapi.GetFilesOwners;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class AlreadyApprovedByPredicate extends OperatorPredicate<ApprovalContext>
    implements Matchable<ApprovalContext> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final GetFilesOwners getFilesOwners;
  private final DiffOperations diffOperations;
  private final UserInPredicate.Field predicateField;

  private static final boolean DISABLE_RENAME_DETECTION = false;
  private static final DiffOptions DO_NOT_IGNORE_REBASE =
      DiffOptions.builder().skipFilesWithAllEditsDueToRebase(false).build();

  public AlreadyApprovedByPredicate(
      GetFilesOwners getFilesOwners,
      DiffOperations diffOperations,
      UserInPredicate.Field predicateField) {
    super("approverin", OPERAND);
    this.getFilesOwners = getFilesOwners;
    this.diffOperations = diffOperations;
    this.predicateField = predicateField;
  }

  @Override
  public boolean match(ApprovalContext ctx) {
    try {
      Account.Id currentApprover = ctx.approverId();
      Project.NameKey project = ctx.changeData().project();
      PatchSet targetPatchSet = ctx.targetPatchSet();
      PatchSet sourcePatchSet = ctx.changeNotes().getPatchSets().get(ctx.sourcePatchSetId());
      Account.Id changeOwner = ctx.changeNotes().getChange().getOwner();
      Account.Id uploader = targetPatchSet.uploader();

      checkState(
          predicateField == UserInPredicate.Field.APPROVER,
          "%s copy-condition can only be applied to `approverin:` predicates. Label %s for user %s"
              + " will NOT be copied.",
          FULL_OPERAND_WITH_PLUGIN_NAME,
          ctx.labelType().getName(),
          currentApprover);

      checkState(
          sourcePatchSet != null,
          "Could not get source patch-set for %s for project %s",
          ctx.sourcePatchSetId(),
          project);

      Map<String, FileDiffOutput> priorVsCurrent =
          diffOperations
              .listModifiedFiles(
                  project,
                  sourcePatchSet.commitId(),
                  targetPatchSet.commitId(),
                  DO_NOT_IGNORE_REBASE)
              .entrySet()
              .stream()
              // COMMIT_MSG has never an owner, we don't ever want to consider it, even if it
              // was modified as part of this patch-set.
              .filter(entry -> !Patch.COMMIT_MSG.equals(entry.getKey()))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

      // We can't simply look at keys because it won't contain the old name of renamed-files.
      Set<String> allFilePathsInDiff =
          priorVsCurrent.values().stream()
              .flatMap(v -> Stream.of(v.newPath(), v.oldPath()))
              .filter(Optional::isPresent)
              .map(Optional::get)
              .collect(Collectors.toSet());

      String branch = ctx.changeData().branchOrThrow().branch();
      Set<String> filesOwnedByApprover =
          getFilesOwners.filterFilesOwnedBy(currentApprover, allFilePathsInDiff, project, branch);

      if (isApproverAlsoOwnerAndUploader(currentApprover, changeOwner, uploader)
          && allTouchedFilesAreOwned(filesOwnedByApprover, allFilePathsInDiff)
          && getFilesOwners.noOwnedFileIsBannedFromAutoApproval(
              filesOwnedByApprover, project, branch)) {
        logger.atFinest().log(
            "Approver '%s' is change owner and uploader. only owned files have been modified and"
                + " none of them has auto-owners-approved=false. Label WILL be copied.",
            currentApprover);
        return true;
      }

      if (!filesOwnedByApprover.isEmpty()) {
        logger.atFinest().log(
            "Approver '%s' owns files that were changed in this new patch set: %s",
            currentApprover, lazy(() -> String.join(",", filesOwnedByApprover)));

        return shouldCopyLabelForOwnedFiles(
            priorVsCurrent.values(), filesOwnedByApprover, currentApprover);
      }

      // The new patchSet has not modified anything I own.
      // I will copy my label, but only if I used to own something in the change.
      try (ObjectInserter ins =
          new InMemoryInserter(ctx.repoView().getRevWalk().getObjectReader())) {

        Map<String, ModifiedFile> baseVsPrior =
            diffOperations.loadModifiedFilesAgainstParentIfNecessary(
                project,
                sourcePatchSet.commitId(),
                getParentNum(targetPatchSet.commitId(), ctx.repoView().getRevWalk()),
                ctx.repoView(),
                ins,
                DISABLE_RENAME_DETECTION);
        boolean oldPatchSetHasFilesOwnedByMe =
            getFilesOwners.isAnyFileOwnedBy(
                currentApprover,
                baseVsPrior.keySet(),
                project,
                ctx.changeData().branchOrThrow().branch());

        logger.atFinest().log(
            "Has approver '%s' ever owned anything in this change? %s",
            currentApprover,
            oldPatchSetHasFilesOwnedByMe
                ? "yes, will copy approval"
                : "No, will not copy approval");

        return oldPatchSetHasFilesOwnedByMe;
      }
    } catch (DiffNotAvailableException | IOException | InvalidOwnersFileException e) {
      throw new StorageException(
          String.format(
              "Failed to compute %s, label will not be copied.", FULL_OPERAND_WITH_PLUGIN_NAME),
          e);
    }
  }

  private static boolean shouldCopyLabelForOwnedFiles(
      Iterable<FileDiffOutput> diffs, Set<String> ownedPaths, Account.Id currentApprover) {

    for (FileDiffOutput diff : diffs) {
      if (!touchesOwnedPath(diff, ownedPaths)) {
        continue;
      }

      if (isPathChange(diff)) {
        logger.atFinest().log(
            "File owned by approver %s has a path change (rename/add/delete): oldPath=%s,"
                + " newPath=%s",
            currentApprover, diff.oldPath().orElse("<none>"), diff.newPath().orElse("<none>"));
        return false;
      }

      // Content changes are acceptable only if they are entirely due to rebase.
      if (!diff.allEditsDueToRebase()) {
        logger.atFinest().log(
            "file owned by approver %s has content edits (that are not only due to rebase):"
                + " path=%s",
            currentApprover, diff.newPath().orElseGet(() -> diff.oldPath().orElse("<unknown>")));
        return false;
      }
    }

    logger.atFinest().log(
        "All edits for file owned by approver %s are either rebase-only or no path changes;"
            + " approval can be copied.",
        currentApprover);
    return true;
  }

  private static boolean touchesOwnedPath(FileDiffOutput d, Set<String> ownedPaths) {
    return d.newPath().filter(ownedPaths::contains).isPresent()
        || d.oldPath().filter(ownedPaths::contains).isPresent();
  }

  private static boolean isPathChange(FileDiffOutput d) {
    // A path change means rename/add/delete: oldPath != newPath, including empty vs present.
    return !d.oldPath().equals(d.newPath());
  }

  private static boolean isApproverAlsoOwnerAndUploader(
      Account.Id currentApprover, Account.Id changeOwner, Account.Id uploader) {
    return currentApprover.equals(changeOwner) && currentApprover.equals(uploader);
  }

  private static boolean allTouchedFilesAreOwned(
      Set<String> filesOwnedByApprover, Set<String> allFilePathsInDiff) {
    return !filesOwnedByApprover.isEmpty()
        && filesOwnedByApprover.size() == allFilePathsInDiff.size();
  }

  private int getParentNum(ObjectId objectId, RevWalk revWalk) {
    try {
      RevCommit commit = revWalk.parseCommit(objectId);
      // merge commit with 2 or more parents: must use parentNum = 1 to compare against the first
      // parent. Using parentNum = 0 would compare against the auto-merge.
      return commit.getParentCount() > 1 ? 1 : 0;
    } catch (IOException ex) {
      throw new StorageException(ex);
    }
  }

  @Override
  public int getCost() {
    return 10;
  }
}
