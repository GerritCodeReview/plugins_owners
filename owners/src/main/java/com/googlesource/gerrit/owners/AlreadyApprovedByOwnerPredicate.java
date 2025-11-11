package com.googlesource.gerrit.owners;

import static com.googlesource.gerrit.owners.AlreadyApprovedByOwnerOperand.*;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.gerrit.server.query.approval.ApprovalContext;
import com.google.gerrit.server.update.RepoView;
import com.googlesource.gerrit.owners.restapi.GetFilesOwners;
import java.util.HashSet;
import java.util.Map;

public class AlreadyApprovedByOwnerPredicate extends OperatorPredicate<ApprovalContext>
    implements Matchable<ApprovalContext> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final GetFilesOwners getFilesOwners;
  private final ChangeResource.Factory changeResourceFactory;
  private final CurrentUser currentUser;
  private final DiffOperations diffOperations;

  public AlreadyApprovedByOwnerPredicate(
      GetFilesOwners getFilesOwners,
      ChangeResource.Factory changeResourceFactory,
      CurrentUser currentUser,
      DiffOperations diffOperations) {
    super("approverin", OPERAND);
    this.getFilesOwners = getFilesOwners;
    this.changeResourceFactory = changeResourceFactory;
    this.currentUser = currentUser;
    this.diffOperations = diffOperations;
  }

  @Override
  public boolean match(ApprovalContext ctx) {
    logger.atWarning().log("Owner Approved Predicate called");

    try (RepoView repoView = ctx.repoView()) {
      Account.Id currentApprover = ctx.approverId();
      PatchSet targetPatchSet = ctx.targetPatchSet();
      PatchSet sourcePatchSet = ctx.changeNotes().getPatchSets().get(ctx.sourcePatchSetId());

      assert sourcePatchSet != null;
      Map<String, ModifiedFile> priorVsCurrent =
          diffOperations.loadModifiedFilesIfNecessary(
              ctx.changeNotes().getProjectName(),
              sourcePatchSet.commitId(),
              targetPatchSet.commitId(),
              repoView.getRevWalk(),
              repoView.getConfig(),
              /* enableRenameDetection= */ false);

      boolean oldPatchSetHasFilesOwnedByMe =
          getFilesOwners.isAnyFileOwnedBy(
              currentApprover,
              new HashSet<>(ctx.changeData().currentFilePaths()),
              ctx.changeData().project(),
              ctx.changeData().branchOrThrow().branch());

      boolean newPatchSetHasFilesOwnedByMe =
          getFilesOwners.isAnyFileOwnedBy(
              currentApprover,
              priorVsCurrent.keySet(),
              ctx.changeData().project(),
              ctx.changeData().branchOrThrow().branch());

      // 1. The new patchSet has modified something that I own, then I must re-approve
      if (newPatchSetHasFilesOwnedByMe) {
        logger.atWarning().log(
            "User %s owns files that were changed in this new patchset, must re-approve",
            currentApprover);
        return false;
      }

      // 1. The new patchSet hasn't changed anything I own. I only copy my label if I own something
      // in
      // the change.
      logger.atWarning().log(
          "User %s ever owned anything in this change? %s",
          currentApprover,
          oldPatchSetHasFilesOwnedByMe ? "yes, will copy approval" : "No, will not copy approval");

      return oldPatchSetHasFilesOwnedByMe;
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Could not compute owner copy condition");
    }
    return false;
  }

  @Override
  public int getCost() {
    return 0;
  }
}
