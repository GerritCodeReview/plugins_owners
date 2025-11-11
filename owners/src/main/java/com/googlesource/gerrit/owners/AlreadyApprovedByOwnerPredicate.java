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
import static com.googlesource.gerrit.owners.AlreadyApprovedByOwnerOperand.FULL_OPERAND_NAME;
import static com.googlesource.gerrit.owners.AlreadyApprovedByOwnerOperand.OPERAND;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.gerrit.server.query.approval.ApprovalContext;
import com.google.gerrit.server.query.approval.UserInPredicate;
import com.googlesource.gerrit.owners.restapi.GetFilesOwners;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class AlreadyApprovedByOwnerPredicate extends OperatorPredicate<ApprovalContext>
    implements Matchable<ApprovalContext> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final GetFilesOwners getFilesOwners;
  private final DiffOperations diffOperations;
  private final UserInPredicate.Field predicateField;

  public AlreadyApprovedByOwnerPredicate(
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

      checkState(
          predicateField == UserInPredicate.Field.APPROVER,
          "%s copy-condition can only be applied to `approverin:` predicates. Label %s for user %s"
              + " will NOT be copied.",
          FULL_OPERAND_NAME,
          ctx.labelType().getName(),
          currentApprover);

      checkState(
          sourcePatchSet != null,
          "Could not get source patch-set for %s for project %s",
          ctx.sourcePatchSetId(),
          project);

      Map<String, ModifiedFile> priorVsCurrent =
          diffOperations.loadModifiedFilesIfNecessary(
              project,
              sourcePatchSet.commitId(),
              targetPatchSet.commitId(),
              ctx.repoView().getRevWalk(),
              ctx.repoView().getConfig(),
              /* enableRenameDetection= */ false);

      boolean newPatchSetHasFilesOwnedByMe =
          getFilesOwners.isAnyFileOwnedBy(
              currentApprover,
              priorVsCurrent.keySet(),
              project,
              ctx.changeData().branchOrThrow().branch());

      if (newPatchSetHasFilesOwnedByMe) {
        logger.atFinest().log(
            "Approver '%s' owns files that were changed in this new patch set, must re-approve",
            currentApprover);
        return false;
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
                /* enableRenameDetection= */ false);
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
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Could not compute %s owner copy condition", OPERAND);
      return false;
    }
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
    return 1;
  }
}
