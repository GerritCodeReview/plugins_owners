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
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status.ERROR;
import static com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status.SATISFIED;
import static com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status.UNSATISFIED;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GlobalPluginConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.SubmitRecordInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.inject.Inject;
import com.googlesource.gerrit.owners.common.LabelDefinition;
import java.util.Collection;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;

@TestPlugin(name = "owners", sysModule = "com.googlesource.gerrit.owners.OwnersModule")
@UseLocalDisk
public class OwnersApprovalHasOperandIT extends OwnersSubmitRequirementITAbstract {
  private static final String REQUIREMENT_NAME = "Owner-Approval";

  @Inject private RequestScopeOperations requestScopeOperations;

  @Before
  public void setup() throws Exception {
    enableSubmitRequirementsForProject(project);
  }

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldOwnersRequirementBeSatisfied() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, LabelDefinition.parse("Code-Review,1").get(), admin2);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get(ListChangesOption.SUBMIT_REQUIREMENTS);
    verifySubmitRequirementsNotReady(changeNotReady);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.recommend());
    ChangeInfo ownersVoteSufficient = forChange(r).get(ListChangesOption.SUBMIT_REQUIREMENTS);
    verifySubmitRequirementsReady(ownersVoteSufficient);
  }

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldRequireAtLeastOneApprovalForMatchingPathFromOwner() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    TestAccount user1 = accountCreator.user1();
    addOwnerFileWithMatchersToRoot(true, ".md", admin2, user1);

    PushOneCommit.Result r = createChange("Add a file", "README.md", "foo");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifySubmitRequirementsNotReady(changeNotReady);

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeNotReadyAfterSelfApproval = changeApi.get();
    assertThat(changeNotReadyAfterSelfApproval.submittable).isFalse();
    verifySubmitRequirementsNotReady(changeNotReady);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    verifySubmitRequirementsReady(changeReady);
  }

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldNotRequireApprovalForNotMatchingPath() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileWithMatchersToRoot(true, ".md", admin2);

    PushOneCommit.Result r = createChange("Add a file", "README.txt", "foo");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    assertThat(changeNotReady.requirements).isEmpty();

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeReady = changeApi.get();
    assertThat(changeReady.submittable).isTrue();
    assertThat(changeReady.requirements).isEmpty();
  }

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldRequireApprovalFromRootOwner() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, admin2);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifySubmitRequirementsNotReady(changeNotReady);

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeNotReadyAfterSelfApproval = changeApi.get();
    assertThat(changeNotReadyAfterSelfApproval.submittable).isFalse();
    verifySubmitRequirementsNotReady(changeNotReadyAfterSelfApproval);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    verifySubmitRequirementsReady(changeReady);
  }

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldBlockOwnersApprovalForMaxNegativeVote() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, admin2);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifySubmitRequirementsNotReady(changeNotReady);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    verifySubmitRequirementsReady(changeReady);

    changeApi.current().review(ReviewInput.reject());
    assertThat(forChange(r).get().submittable).isFalse();
  }

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldRequireVerifiedApprovalEvenIfCodeOwnerApproved() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, admin2);

    installVerifiedLabel();

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    assertThat(changeApi.get().submittable).isFalse();
    verifySubmitRequirementsNotReady(changeApi.get());

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.approve());
    assertThat(forChange(r).get().submittable).isFalse();
    verifySubmitRequirementsReady(forChange(r).get());
    verifyHasSubmitRecord(
        forChange(r).get().submitRecords, LabelId.VERIFIED, SubmitRecordInfo.Label.Status.NEED);

    changeApi.current().review(new ReviewInput().label(LabelId.VERIFIED, 1));
    assertThat(changeApi.get().submittable).isTrue();
    verifyHasSubmitRecord(
        changeApi.get().submitRecords, LabelId.VERIFIED, SubmitRecordInfo.Label.Status.OK);
  }

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldRequireCodeOwnerApprovalEvenIfVerifiedWasApproved() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, admin2);

    installVerifiedLabel();

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    assertThat(changeApi.get().submittable).isFalse();
    verifySubmitRequirementsNotReady(changeApi.get());

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(new ReviewInput().label(LabelId.VERIFIED, 1));
    ChangeInfo changeNotReady = forChange(r).get();
    assertThat(changeNotReady.submittable).isFalse();
    verifySubmitRequirementsNotReady(changeNotReady);
    verifyHasSubmitRecord(
        changeNotReady.submitRecords, LabelId.VERIFIED, SubmitRecordInfo.Label.Status.OK);

    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    verifySubmitRequirementsReady(changeReady);
  }

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldRequireConfiguredLabelByCodeOwner() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    String labelId = "Foo";
    addOwnerFileToRoot(true, LabelDefinition.parse(labelId).get(), admin2);

    installLabel(labelId);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    assertThat(changeApi.get().submittable).isFalse();
    verifySubmitRequirementsNotReady(changeApi.get());

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeStillNotReady = changeApi.get();
    assertThat(changeStillNotReady.submittable).isFalse();
    verifySubmitRequirementsNotReady(changeStillNotReady);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(new ReviewInput().label(labelId, 1));
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    verifySubmitRequirementsReady(changeReady);
    verifyHasSubmitRecord(changeReady.submitRecords, labelId, SubmitRecordInfo.Label.Status.OK);
  }

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldRequireConfiguredLabelByCodeOwnerEvenIfItIsNotConfiguredForProject()
      throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    String notExistinglabelId = "Foo";
    addOwnerFileToRoot(true, LabelDefinition.parse(notExistinglabelId).get(), admin2);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    assertThat(changeApi.get().submittable).isFalse();
    verifySubmitRequirementsNotReady(changeApi.get());

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeStillNotReady = changeApi.get();
    assertThat(changeStillNotReady.submittable).isFalse();
    verifySubmitRequirementsNotReady(changeStillNotReady);
  }

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldRequireConfiguredLabelScoreByCodeOwner() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, LabelDefinition.parse("Code-Review,1").get(), admin2);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifySubmitRequirementsNotReady(changeNotReady);

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeNotReadyAfterSelfApproval = changeApi.get();
    assertThat(changeNotReadyAfterSelfApproval.submittable).isFalse();
    verifySubmitRequirementsNotReady(changeNotReadyAfterSelfApproval);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.recommend());
    ChangeInfo changeReadyWithOwnerScore = forChange(r).get();
    assertThat(changeReadyWithOwnerScore.submittable).isTrue();
    verifySubmitRequirementsReady(changeReadyWithOwnerScore);
  }

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldConfiguredLabelScoreByCodeOwnerBeNotSufficientIfLabelRequiresMaxValue()
      throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, LabelDefinition.parse("Code-Review,1").get(), admin2);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifySubmitRequirementsNotReady(changeNotReady);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.recommend());
    ChangeInfo ownersVoteNotSufficient = changeApi.get();
    assertThat(ownersVoteNotSufficient.submittable).isFalse();
    verifySubmitRequirementsReady(ownersVoteNotSufficient);
    verifyHasSubmitRecord(
        ownersVoteNotSufficient.submitRecords,
        LabelId.CODE_REVIEW,
        SubmitRecordInfo.Label.Status.NEED);

    requestScopeOperations.setApiUser(admin.id());
    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReadyWithMaxScore = forChange(r).get();
    assertThat(changeReadyWithMaxScore.submittable).isTrue();
    verifySubmitRequirementsReady(changeReadyWithMaxScore);
    verifyHasSubmitRecord(
        changeReadyWithMaxScore.submitRecords,
        LabelId.CODE_REVIEW,
        SubmitRecordInfo.Label.Status.OK);
  }

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldConfiguredLabelScoreByCodeOwnersOverwriteSubmitRequirement() throws Exception {
    installLabel(TestLabels.codeReview().toBuilder().setFunction(LabelFunction.NO_OP).build());

    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, LabelDefinition.parse("Code-Review,1").get(), admin2);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifySubmitRequirementsNotReady(changeNotReady);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.recommend());
    ChangeInfo ownersVoteSufficient = forChange(r).get();
    assertThat(ownersVoteSufficient.submittable).isTrue();
    verifySubmitRequirementsReady(ownersVoteSufficient);
  }

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldRequireApprovalFromGrandParentProjectOwner() throws Exception {
    Project.NameKey parentProjectName =
        createProjectOverAPI("parent", allProjects, true, SubmitType.FAST_FORWARD_ONLY);
    Project.NameKey childProjectName =
        createProjectOverAPI("child", parentProjectName, true, SubmitType.FAST_FORWARD_ONLY);
    enableSubmitRequirementsForProject(childProjectName);
    TestRepository<InMemoryRepository> childRepo = cloneProject(childProjectName);

    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRefsMetaConfig(true, admin2, allProjects);

    PushOneCommit.Result r =
        createCommitAndPush(childRepo, "refs/for/master", "Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifySubmitRequirementsNotReady(changeNotReady);

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeNotReadyAfterSelfApproval = changeApi.get();
    assertThat(changeNotReadyAfterSelfApproval.submittable).isFalse();
    verifySubmitRequirementsNotReady(changeNotReadyAfterSelfApproval);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    verifySubmitRequirementsReady(changeReady);
  }

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldIndicateSubmitRequirementErrorForBrokenOwnersFile() throws Exception {
    addBrokenOwnersFileToRoot();

    PushOneCommit.Result r = createChange("Add a file", "README.md", "foo");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifySubmitRequirementsError(changeNotReady);
  }

  private void verifySubmitRequirementsNotReady(ChangeInfo change) {
    verifySubmitRequirements(change.submitRequirements, REQUIREMENT_NAME, UNSATISFIED);
  }

  private void verifySubmitRequirementsReady(ChangeInfo change) {
    verifySubmitRequirements(change.submitRequirements, REQUIREMENT_NAME, SATISFIED);
  }

  private void verifySubmitRequirementsError(ChangeInfo change) {
    verifySubmitRequirements(change.submitRequirements, REQUIREMENT_NAME, ERROR);
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

  private void enableSubmitRequirementsForProject(Project.NameKey forProject) throws Exception {
    configSubmitRequirement(
        forProject,
        SubmitRequirement.builder()
            .setName(REQUIREMENT_NAME)
            .setSubmittabilityExpression(SubmitRequirementExpression.create("has:approval_owners"))
            .setAllowOverrideInChildProjects(false)
            .build());
  }
}
