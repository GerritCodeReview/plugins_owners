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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static java.util.stream.Collectors.joining;

import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.SubmitRecordInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.inject.Inject;
import com.googlesource.gerrit.owners.common.LabelDefinition;
import java.util.Collection;
import java.util.stream.Stream;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

abstract class OwnersSubmitRequirementITAbstract extends LightweightPluginDaemonTest {
  @Inject protected RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @Test
  public void shouldRequireAtLeastOneApprovalForMatchingPathFromOwner() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    TestAccount user1 = accountCreator.user1();
    addOwnerFileWithMatchersToRoot(true, ".md", admin2, user1);

    PushOneCommit.Result r = createChange("Add a file", "README.md", "foo");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifyChangeNotReady(changeNotReady);

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeNotReadyAfterSelfApproval = changeApi.get();
    assertThat(changeNotReadyAfterSelfApproval.submittable).isFalse();
    verifyChangeNotReady(changeNotReadyAfterSelfApproval);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    verifyChangeReady(changeReady);
  }

  @Test
  public void shouldNotRequireApprovalForNotMatchingPath() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileWithMatchersToRoot(true, ".md", admin2);

    PushOneCommit.Result r = createChange("Add a file", "README.txt", "foo");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    assertThat(changeNotReady.requirements).isEmpty();

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    assertThat(changeReady.requirements).isEmpty();
  }

  @Test
  public void shouldRequireApprovalFromRootOwner() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, admin2);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifyChangeNotReady(changeNotReady);

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeNotReadyAfterSelfApproval = changeApi.get();
    assertThat(changeNotReadyAfterSelfApproval.submittable).isFalse();
    verifyChangeNotReady(changeNotReadyAfterSelfApproval);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    verifyChangeReady(changeReady);
  }

  @Test
  public void shouldBlockOwnersApprovalForMaxNegativeVote() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, admin2);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifyChangeNotReady(changeNotReady);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    verifyChangeReady(changeReady);

    changeApi.current().review(ReviewInput.reject());
    assertThat(forChange(r).get().submittable).isFalse();
  }

  @Test
  public void shouldRequireVerifiedApprovalEvenIfCodeOwnerApproved() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, admin2);

    installVerifiedLabel();

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    assertThat(changeApi.get().submittable).isFalse();
    verifyChangeNotReady(changeApi.get());

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.approve());
    assertThat(forChange(r).get().submittable).isFalse();
    verifyChangeReady(forChange(r).get());
    verifyHasSubmitRecord(
        forChange(r).get().submitRecords, LabelId.VERIFIED, SubmitRecordInfo.Label.Status.NEED);

    changeApi.current().review(new ReviewInput().label(LabelId.VERIFIED, 1));
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    verifyHasSubmitRecord(
        changeReady.submitRecords, LabelId.VERIFIED, SubmitRecordInfo.Label.Status.OK);
  }

  @Test
  public void shouldRequireCodeOwnerApprovalEvenIfVerifiedWasApproved() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, admin2);

    installVerifiedLabel();

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    assertThat(changeApi.get().submittable).isFalse();
    verifyChangeNotReady(changeApi.get());

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(new ReviewInput().label(LabelId.VERIFIED, 1));
    ChangeInfo changeNotReady = forChange(r).get();
    assertThat(changeNotReady.submittable).isFalse();
    verifyChangeNotReady(changeNotReady);
    verifyHasSubmitRecord(
        changeNotReady.submitRecords, LabelId.VERIFIED, SubmitRecordInfo.Label.Status.OK);

    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    verifyChangeReady(changeReady);
  }

  @Test
  public void shouldRequireConfiguredLabelByCodeOwner() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    String labelId = "Foo";
    addOwnerFileToRoot(true, LabelDefinition.parse(labelId).get(), admin2);

    installLabel(labelId);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    assertThat(changeApi.get().submittable).isFalse();
    verifyChangeNotReady(changeApi.get());

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeStillNotReady = changeApi.get();
    assertThat(changeStillNotReady.submittable).isFalse();
    verifyChangeNotReady(changeStillNotReady);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(new ReviewInput().label(labelId, 1));
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    verifyChangeReady(changeReady);
    verifyHasSubmitRecord(changeReady.submitRecords, labelId, SubmitRecordInfo.Label.Status.OK);
  }

  @Test
  public void shouldRequireConfiguredLabelByCodeOwnerEvenIfItIsNotConfiguredForProject()
      throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    String notExistinglabelId = "Foo";
    addOwnerFileToRoot(true, LabelDefinition.parse(notExistinglabelId).get(), admin2);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    assertThat(changeApi.get().submittable).isFalse();
    verifyChangeNotReady(changeApi.get());

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeStillNotReady = changeApi.get();
    assertThat(changeStillNotReady.submittable).isFalse();
    verifyChangeNotReady(changeStillNotReady);
  }

  @Test
  public void shouldRequireConfiguredLabelScoreByCodeOwner() throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, LabelDefinition.parse("Code-Review,1").get(), admin2);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifyChangeNotReady(changeNotReady);

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeNotReadyAfterSelfApproval = changeApi.get();
    assertThat(changeNotReadyAfterSelfApproval.submittable).isFalse();
    verifyChangeNotReady(changeNotReadyAfterSelfApproval);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.recommend());
    ChangeInfo changeReadyWithOwnerScore = forChange(r).get();
    assertThat(changeReadyWithOwnerScore.submittable).isTrue();
    verifyChangeReady(changeReadyWithOwnerScore);
  }

  @Test
  public void shouldConfiguredLabelScoreByCodeOwnerBeNotSufficientIfLabelRequiresMaxValue()
      throws Exception {
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, LabelDefinition.parse("Code-Review,1").get(), admin2);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifyChangeNotReady(changeNotReady);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.recommend());
    ChangeInfo ownersVoteNotSufficient = forChange(r).get();
    assertThat(ownersVoteNotSufficient.submittable).isFalse();
    verifyChangeReady(ownersVoteNotSufficient);
    verifyHasSubmitRecord(
        ownersVoteNotSufficient.submitRecords,
        LabelId.CODE_REVIEW,
        SubmitRecordInfo.Label.Status.NEED);

    requestScopeOperations.setApiUser(admin.id());
    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReadyWithMaxScore = forChange(r).get();
    assertThat(changeReadyWithMaxScore.submittable).isTrue();
    verifyChangeReady(changeReadyWithMaxScore);
    verifyHasSubmitRecord(
        changeReadyWithMaxScore.submitRecords,
        LabelId.CODE_REVIEW,
        SubmitRecordInfo.Label.Status.OK);
  }

  @Test
  public void shouldConfiguredLabelScoreByCodeOwnersOverwriteSubmitRequirement() throws Exception {
    installLabel(TestLabels.codeReview().toBuilder().setFunction(LabelFunction.NO_OP).build());

    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, LabelDefinition.parse("Code-Review,1").get(), admin2);

    PushOneCommit.Result r = createChange("Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifyChangeNotReady(changeNotReady);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.recommend());
    ChangeInfo ownersVoteSufficient = forChange(r).get();
    assertThat(ownersVoteSufficient.submittable).isTrue();
    verifyChangeReady(ownersVoteSufficient);
  }

  @Test
  public void shouldRequireApprovalFromGrandParentProjectOwner() throws Exception {
    Project.NameKey parentProjectName =
        createProjectOverAPI("parent", allProjects, true, SubmitType.FAST_FORWARD_ONLY);
    Project.NameKey childProjectName =
        createProjectOverAPI("child", parentProjectName, true, SubmitType.FAST_FORWARD_ONLY);
    updateChildProjectConfiguration(childProjectName);
    TestRepository<InMemoryRepository> childRepo = cloneProject(childProjectName);

    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRefsMetaConfig(true, admin2, allProjects);

    PushOneCommit.Result r =
        createCommitAndPush(childRepo, "refs/for/master", "Add a file", "foo", "bar");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    verifyChangeNotReady(changeNotReady);

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeNotReadyAfterSelfApproval = changeApi.get();
    assertThat(changeNotReadyAfterSelfApproval.submittable).isFalse();
    verifyChangeNotReady(changeNotReadyAfterSelfApproval);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    verifyChangeReady(changeReady);
  }

  @Test
  public void shouldIndicateRuleErrorForBrokenOwnersFile() throws Exception {
    addBrokenOwnersFileToRoot();

    PushOneCommit.Result r = createChange("Add a file", "README.md", "foo");
    ChangeApi changeApi = forChange(r);
    ChangeInfo changeNotReady = changeApi.get();
    assertThat(changeNotReady.submittable).isFalse();
    assertThat(changeNotReady.requirements).isEmpty();
    verifyRuleError(changeNotReady);
  }

  protected abstract void verifyChangeNotReady(ChangeInfo notReady);

  protected abstract void verifyChangeReady(ChangeInfo ready);

  protected abstract void verifyRuleError(ChangeInfo change);

  protected abstract void updateChildProjectConfiguration(Project.NameKey childProject)
      throws Exception;

  private void verifyHasSubmitRecord(
      Collection<SubmitRecordInfo> records, String label, SubmitRecordInfo.Label.Status status) {
    assertThat(
            records.stream()
                .flatMap(record -> record.labels.stream())
                .filter(l -> l.label.equals(label) && l.status == status)
                .findAny())
        .isPresent();
  }

  private void installVerifiedLabel() throws Exception {
    installLabel(LabelId.VERIFIED);
  }

  private void installLabel(String labelId) throws Exception {
    LabelType verified =
        labelBuilder(labelId, value(1, "Verified"), value(0, "No score"), value(-1, "Fails"))
            .setFunction(LabelFunction.MAX_WITH_BLOCK)
            .build();

    installLabel(verified);

    String heads = RefNames.REFS_HEADS + "*";
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel(verified.getName()).ref(heads).group(REGISTERED_USERS).range(-1, 1))
        .update();
  }

  private void installLabel(LabelType label) throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().upsertLabelType(label);
      u.save();
    }
  }

  protected ChangeApi forChange(PushOneCommit.Result r) throws RestApiException {
    return gApi.changes().id(r.getChangeId());
  }

  private void addBrokenOwnersFileToRoot() throws Exception {
    pushOwnersToMaster("{foo");
  }

  private void addOwnerFileWithMatchersToRoot(
      boolean inherit, String extension, TestAccount... users) throws Exception {
    // Add OWNERS file to root:
    //
    // inherited: true
    // matchers:
    // - suffix: extension
    //   owners:
    //   - u1.email()
    //   - ...
    //   - uN.email()
    pushOwnersToMaster(
        String.format(
            "inherited: %s\nmatchers:\n" + "- suffix: %s\n  owners:\n%s",
            inherit,
            extension,
            Stream.of(users)
                .map(user -> String.format("   - %s\n", user.email()))
                .collect(joining())));
  }

  private void addOwnerFileToRoot(boolean inherit, TestAccount u) throws Exception {
    // Add OWNERS file to root:
    //
    // inherited: true
    // owners:
    // - u.email()
    pushOwnersToMaster(String.format("inherited: %s\nowners:\n- %s\n", inherit, u.email()));
  }

  private void addOwnerFileToRefsMetaConfig(
      boolean inherit, TestAccount u, Project.NameKey projectName) throws Exception {
    // Add OWNERS file to root:
    //
    // inherited: true
    // owners:
    // - u.email()
    pushOwnersToRefsMetaConfig(
        String.format("inherited: %s\nowners:\n- %s\n", inherit, u.email()), projectName);
  }

  protected void addOwnerFileToRoot(boolean inherit, LabelDefinition label, TestAccount u)
      throws Exception {
    // Add OWNERS file to root:
    //
    // inherited: true
    // label: label,score # score is optional
    // owners:
    // - u.email()
    pushOwnersToMaster(
        String.format(
            "inherited: %s\nlabel: %s\nowners:\n- %s\n",
            inherit,
            String.format(
                "%s%s",
                label.getName(),
                label.getScore().map(value -> String.format(",%d", value)).orElse("")),
            u.email()));
  }

  private void pushOwnersToMaster(String owners) throws Exception {
    pushFactory
        .create(admin.newIdent(), testRepo, "Add OWNER file", "OWNERS", owners)
        .to(RefNames.fullName("master"))
        .assertOkStatus();
  }

  private void pushOwnersToRefsMetaConfig(String owners, Project.NameKey projectName)
      throws Exception {
    TestRepository<InMemoryRepository> project = cloneProject(projectName);
    GitUtil.fetch(project, RefNames.REFS_CONFIG + ":" + RefNames.REFS_CONFIG);
    project.reset(RefNames.REFS_CONFIG);
    pushFactory
        .create(admin.newIdent(), project, "Add OWNER file", "OWNERS", owners)
        .to(RefNames.REFS_CONFIG)
        .assertOkStatus();
  }
}
