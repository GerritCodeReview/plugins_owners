// Copyright (C) 2024 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status.SATISFIED;
import static com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status.UNSATISFIED;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Collection;
import org.junit.Test;

@TestPlugin(name = "owners", sysModule = "com.googlesource.gerrit.owners.OwnersModule")
@UseLocalDisk
public class OwnersRegressionIT extends LightweightPluginDaemonTest {
  private static final String GERRIT_SUBMIT_REQUIREMENT = "Code-Review";

  @Test
  public void shouldNotAffectSubmitWhenSubmitRequirementIsDisabledAndNotConfiguredForProject()
      throws Exception {
    // given that `.md` files are owned by `user1`
    TestAccount user1 = accountCreator.user1();
    addOwnerFileWithMatchersToRoot(true, ".md", user1);

    // when change with README.md is created
    PushOneCommit.Result r = createChange("Add a file", "README.md", "foo");

    // then it is not ready due to Gerrit's default submit requirement
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    assertThat(changeNotReady.requirements).isEmpty();
    verifySubmitRequirement(
        changeNotReady.submitRequirements, GERRIT_SUBMIT_REQUIREMENT, UNSATISFIED);

    // and when a vote is cast by someone else then `user1`
    changeApi.current().review(ReviewInput.approve());

    // then it should be ready to submit
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    assertThat(changeReady.requirements).isEmpty();
    verifySubmitRequirement(changeReady.submitRequirements, GERRIT_SUBMIT_REQUIREMENT, SATISFIED);
  }

  private void verifySubmitRequirement(
      Collection<SubmitRequirementResultInfo> requirements, String name, Status status) {
    assertThat(requirements).hasSize(1);

    SubmitRequirementResultInfo requirement = requirements.iterator().next();
    if (!requirement.name.equals(name) || !(requirement.status == status)) {
      throw new AssertionError(
          String.format(
              "No submit requirement %s with status %s (existing = %s)",
              name,
              status,
              requirements.stream()
                  .map(r -> String.format("%s=%s", r.name, r.status))
                  .collect(toImmutableList())));
    }
  }

  private ChangeApi forChange(PushOneCommit.Result r) throws RestApiException {
    return gApi.changes().id(r.getChangeId());
  }

  private void addOwnerFileWithMatchersToRoot(boolean inherit, String extension, TestAccount owner)
      throws Exception {
    // Add OWNERS file to root:
    //
    // inherited: true
    // matchers:
    // - suffix: extension
    //   owners:
    //   - u1.email()
    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "Add OWNER file",
            "OWNERS",
            String.format(
                "inherited: %s\nmatchers:\n" + "- suffix: %s\n  owners:\n%s",
                inherit, extension, String.format("   - %s\n", owner.email())))
        .to(RefNames.fullName("master"))
        .assertOkStatus();
  }
}
