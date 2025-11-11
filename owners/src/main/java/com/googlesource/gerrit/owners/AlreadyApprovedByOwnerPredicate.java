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

import static com.googlesource.gerrit.owners.AlreadyApprovedByOwnerOperand.*;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.gerrit.server.query.approval.ApprovalContext;
import com.googlesource.gerrit.owners.restapi.GetFilesOwners;
import java.util.HashSet;
import java.util.Map;

public class AlreadyApprovedByOwnerPredicate extends OperatorPredicate<ApprovalContext>
    implements Matchable<ApprovalContext> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final GetFilesOwners getFilesOwners;
  private final DiffOperations diffOperations;

  public AlreadyApprovedByOwnerPredicate(
      GetFilesOwners getFilesOwners, DiffOperations diffOperations) {
    super("approverin", OPERAND);
    this.getFilesOwners = getFilesOwners;
    this.diffOperations = diffOperations;
  }

  @Override
  public boolean match(ApprovalContext ctx) {
    logger.atWarning().log("Owner Approved Predicate called");

    try {
      Account.Id currentApprover = ctx.approverId();
      PatchSet targetPatchSet = ctx.targetPatchSet();
      PatchSet sourcePatchSet = ctx.changeNotes().getPatchSets().get(ctx.sourcePatchSetId());

      assert sourcePatchSet != null;
      Map<String, ModifiedFile> priorVsCurrent =
          diffOperations.loadModifiedFilesIfNecessary(
              ctx.changeNotes().getProjectName(),
              sourcePatchSet.commitId(),
              targetPatchSet.commitId(),
              ctx.repoView().getRevWalk(),
              ctx.repoView().getConfig(),
              /* enableRenameDetection= */ false);

      boolean newPatchSetHasFilesOwnedByMe =
          getFilesOwners.isAnyFileOwnedBy(
              currentApprover,
              priorVsCurrent.keySet(),
              ctx.changeData().project(),
              ctx.changeData().branchOrThrow().branch());

      // The new patchSet has modified something that I own, then I must re-approve
      if (newPatchSetHasFilesOwnedByMe) {
        logger.atWarning().log(
            "Approver '%s' owns files that were changed in this new patch set, must re-approve",
            currentApprover);
        return false;
      }

      // The new patchSet ha not modified anything I own.
      // I will copy my label, but only if I used to own something in the change.
      boolean oldPatchSetHasFilesOwnedByMe =
          getFilesOwners.isAnyFileOwnedBy(
              currentApprover,
              new HashSet<>(ctx.changeData().currentFilePaths()),
              ctx.changeData().project(),
              ctx.changeData().branchOrThrow().branch());

      logger.atWarning().log(
          "Has approver '%s' ever owned anything in this change? %s",
          currentApprover,
          oldPatchSetHasFilesOwnedByMe ? "yes, will copy approval" : "No, will not copy approval");

      return oldPatchSetHasFilesOwnedByMe;
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Could not compute %s owner copy condition", OPERAND);
      return false;
    }
  }

  @Override
  public int getCost() {
    return 0;
  }
}
