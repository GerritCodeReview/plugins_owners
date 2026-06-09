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
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status.ERROR;
import static com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status.SATISFIED;
import static com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status.UNSATISFIED;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status;
import com.googlesource.gerrit.owners.common.LabelDefinition;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;

@TestPlugin(name = "owners", sysModule = "com.googlesource.gerrit.owners.OwnersModule")
@UseLocalDisk
public class OwnersApprovalHasOperandIT extends OwnersSubmitRequirementITAbstract {
  private static final String REQUIREMENT_NAME = "Owner-Approval";

  @Before
  public void setup() throws Exception {
    enableSubmitRequirementsPredicateForProject(project);
  }

  @Test
  public void shouldOwnersRequirementBeSatisfied() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, LabelDefinition.parse("Code-Review,1").get(), admin2);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get(ListChangesOption.SUBMIT_REQUIREMENTS);
    verifyChangeNotReady(changeNotReady);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.recommend());
    ChangeInfo ownersVoteSufficient = forChange(r).get(ListChangesOption.SUBMIT_REQUIREMENTS);
    verifyChangeReady(ownersVoteSufficient);
  }

  @Override
  protected void verifyChangeNotReady(ChangeInfo notReady) {
    verifySubmitRequirements(notReady.submitRequirements, REQUIREMENT_NAME, UNSATISFIED);
  }

  @Override
  protected void verifyChangeReady(ChangeInfo ready) {
    verifySubmitRequirements(ready.submitRequirements, REQUIREMENT_NAME, SATISFIED);
  }

  @Override
  protected void verifyRuleError(ChangeInfo change) {
    verifySubmitRequirements(change.submitRequirements, REQUIREMENT_NAME, ERROR);
  }

  @Override
  protected void updateChildProjectConfiguration(NameKey childProject) throws Exception {
    // submit requirement predicate has to be configured either on a project level or somewhere in
    // the project hierarchy
    enableSubmitRequirementsPredicateForProject(childProject);
  }

  private void verifySubmitRequirements(
      Collection<SubmitRequirementResultInfo> requirements, String name, Status status) {
    for (SubmitRequirementResultInfo requirement : requirements) {
      if (requirement.name.equals(name) && requirement.status == status) {
        return;
      }
    }

    throw new AssertionError(
        String.format(
            "Could not find submit requirement %s with status %s (results = %s)",
            name,
            status,
            requirements.stream()
                .map(r -> String.format("%s=%s", r.name, r.status))
                .collect(toImmutableList())));
  }

  private void enableSubmitRequirementsPredicateForProject(Project.NameKey forProject)
      throws Exception {
    configSubmitRequirement(
        forProject,
        SubmitRequirement.builder()
            .setName(REQUIREMENT_NAME)
            .setSubmittabilityExpression(SubmitRequirementExpression.create("has:approval_owners"))
            .setAllowOverrideInChildProjects(false)
            .build());
  }
}
