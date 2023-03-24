// Copyright (C) 2023 The Android Open Source Project
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

import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SubmitRequirementPredicate;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

/**
 * A predicate that checks if a given change has all necessary owner approvals. Matches with changes
 * that have an owner approval. This predicate wraps the existing {@link OwnersSubmitRequirement}
 * (that implements the {@link SubmitRule}) to perform the logic.
 */
@Singleton
class OwnersApprovalHasPredicate extends SubmitRequirementPredicate {

  private final OwnersSubmitRequirement ownersSubmitRequirement;

  @Inject
  OwnersApprovalHasPredicate(
      @PluginName String pluginName, OwnersSubmitRequirement ownersSubmitRequirement) {
    super("has", OwnersApprovalHasOperand.OPERAND + "_" + pluginName);
    this.ownersSubmitRequirement = ownersSubmitRequirement;
  }

  @Override
  public boolean match(ChangeData cd) {
    Optional<SubmitRecord> submitRecord = ownersSubmitRequirement.evaluate(cd);
    return submitRecord.map(sr -> sr.status == SubmitRecord.Status.OK).orElse(false);
  }

  /**
   * Assuming that it is similarly expensive to calculate this as the 'code-owners' plugin hence
   * giving the same value.
   */
  @Override
  public int getCost() {
    return 10;
  }
}
