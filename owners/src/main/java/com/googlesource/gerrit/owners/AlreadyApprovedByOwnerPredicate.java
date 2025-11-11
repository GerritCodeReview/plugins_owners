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
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.gerrit.server.query.approval.ApprovalContext;
import com.googlesource.gerrit.owners.restapi.GetFilesOwners;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

public class AlreadyApprovedByOwnerPredicate extends OperatorPredicate<ApprovalContext>
    implements Matchable<ApprovalContext> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final GetFilesOwners getFilesOwners;
  private final DiffOperations diffOperations;
  private final GitRepositoryManager repositoryManager;

  public AlreadyApprovedByOwnerPredicate(
      GetFilesOwners getFilesOwners,
      DiffOperations diffOperations,
      GitRepositoryManager repositoryManager) {
    super("approverin", OPERAND);
    this.getFilesOwners = getFilesOwners;
    this.diffOperations = diffOperations;
    this.repositoryManager = repositoryManager;
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

      // The new patchSet has not modified anything I own.
      // I will copy my label, but only if I used to own something in the change.
      try (ObjectInserter ins =
          new InMemoryInserter(ctx.repoView().getRevWalk().getObjectReader())) {
        int parentNum =
            isInitialCommit(ctx.changeNotes().getProjectName(), targetPatchSet.commitId()) ? 0 : 1;
        Map<String, ModifiedFile> baseVsPrior =
            diffOperations.loadModifiedFilesAgainstParentIfNecessary(
                ctx.changeNotes().getProjectName(),
                sourcePatchSet.commitId(),
                parentNum,
                ctx.repoView(),
                ins,
                /* enableRenameDetection= */ false);
        boolean oldPatchSetHasFilesOwnedByMe =
            getFilesOwners.isAnyFileOwnedBy(
                currentApprover,
                baseVsPrior.keySet(),
                ctx.changeData().project(),
                ctx.changeData().branchOrThrow().branch());

        logger.atWarning().log(
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

  private boolean isInitialCommit(Project.NameKey project, ObjectId objectId) {
    try (Repository repo = repositoryManager.openRepository(project);
        RevWalk revWalk = new RevWalk(repo)) {
      return revWalk.parseCommit(objectId).getParentCount() == 0;
    } catch (IOException ex) {
      throw new StorageException(ex);
    }
  }

  @Override
  public int getCost() {
    return 0;
  }
}
