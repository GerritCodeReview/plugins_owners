// Copyright (C) 2026 The Android Open Source Project
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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.googlesource.gerrit.owners.common.InvalidOwnersFileException;
import com.googlesource.gerrit.owners.restapi.GetFilesOwners;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class AutoOwnersApprovalFunctions {
  private static final DiffOptions DO_NOT_IGNORE_REBASE =
      DiffOptions.builder().skipFilesWithAllEditsDueToRebase(false).build();

  public static Map<String, FileDiffOutput> modifiedFilesBetweenPatchSets(
      DiffOperations diffOperations,
      Project.NameKey project,
      PatchSet sourcePatchSet,
      PatchSet targetPatchSet)
      throws DiffNotAvailableException {
    return diffOperations
        .listModifiedFiles(
            project, sourcePatchSet.commitId(), targetPatchSet.commitId(), DO_NOT_IGNORE_REBASE)
        .entrySet()
        .stream()
        // COMMIT_MSG has never an owner, we don't ever want to consider it, even if it
        // was modified as part of this patch-set.
        .filter(entry -> !Patch.COMMIT_MSG.equals(entry.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public static Set<String> touchedPaths(Map<String, FileDiffOutput> priorVsCurrent) {
    return priorVsCurrent.values().stream()
        .flatMap(v -> Stream.of(v.newPath(), v.oldPath()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());
  }

  public static boolean allowsAutoApprovalOnPatch(
      Account.Id approver,
      Account.Id changeOwner,
      Account.Id uploader,
      Set<String> filesOwnedByApprover,
      Set<String> allTouchedFiles,
      GetFilesOwners getFilesOwners,
      Project.NameKey project,
      String branch)
      throws IOException, InvalidOwnersFileException {
    return approver.equals(changeOwner)
        && approver.equals(uploader)
        && !filesOwnedByApprover.isEmpty()
        && filesOwnedByApprover.size() == allTouchedFiles.size()
        && getFilesOwners.allOwnedFilesAllowAutoApproval(filesOwnedByApprover, project, branch);
  }
}
