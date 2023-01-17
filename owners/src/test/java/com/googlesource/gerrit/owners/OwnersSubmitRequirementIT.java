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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static java.util.stream.Collectors.joining;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GlobalPluginConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LegacySubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRecordInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.junit.Test;

@TestPlugin(name = "owners", sysModule = "com.googlesource.gerrit.owners.OwnersModule")
@UseLocalDisk
public class OwnersSubmitRequirementIT extends LightweightPluginDaemonTest {
  private static final LegacySubmitRequirementInfo NOT_READY =
      new LegacySubmitRequirementInfo("NOT_READY", "Owners", "owners");
  private static final LegacySubmitRequirementInfo READY =
      new LegacySubmitRequirementInfo("OK", "Owners", "owners");

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

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
    assertThat(changeNotReady.requirements).containsExactly(NOT_READY);

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeNotReadyAfterSelfApproval = changeApi.get();
    assertThat(changeNotReadyAfterSelfApproval.submittable).isFalse();
    assertThat(changeNotReadyAfterSelfApproval.requirements).containsExactly(NOT_READY);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    assertThat(changeReady.requirements).containsExactly(READY);
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
    assertThat(changeNotReady.requirements).containsExactly(NOT_READY);

    changeApi.current().review(ReviewInput.approve());
    ChangeInfo changeNotReadyAfterSelfApproval = changeApi.get();
    assertThat(changeNotReadyAfterSelfApproval.submittable).isFalse();
    assertThat(changeNotReadyAfterSelfApproval.requirements).containsExactly(NOT_READY);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    assertThat(changeReady.requirements).containsExactly(READY);
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
    assertThat(changeNotReady.requirements).containsExactly(NOT_READY);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    assertThat(changeReady.requirements).containsExactly(READY);

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
    assertThat(changeApi.get().requirements).containsExactly(NOT_READY);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(ReviewInput.approve());
    assertThat(forChange(r).get().submittable).isFalse();
    assertThat(forChange(r).get().requirements).containsExactly(READY);
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
    assertThat(changeApi.get().requirements).containsExactly(NOT_READY);

    requestScopeOperations.setApiUser(admin2.id());
    forChange(r).current().review(new ReviewInput().label(LabelId.VERIFIED, 1));
    ChangeInfo changeNotReady = forChange(r).get();
    assertThat(changeNotReady.submittable).isFalse();
    assertThat(changeNotReady.requirements).containsExactly(NOT_READY);
    verifyHasSubmitRecord(
        changeNotReady.submitRecords, LabelId.VERIFIED, SubmitRecordInfo.Label.Status.OK);

    forChange(r).current().review(ReviewInput.approve());
    ChangeInfo changeReady = forChange(r).get();
    assertThat(changeReady.submittable).isTrue();
    assertThat(changeReady.requirements).containsExactly(READY);
  }

  private void verifyHasSubmitRecord(
      Collection<SubmitRecordInfo> records, String label, SubmitRecordInfo.Label.Status status) {
    assertThat(
            records.stream()
                .flatMap(record -> record.labels.stream())
                .filter(l -> l.label.equals(label) && l.status == status)
                .findAny())
        .isPresent();
  }

  private void installVerifiedLabel()
      throws IOException, ConfigInvalidException, RepositoryNotFoundException {
    LabelType verified =
        labelBuilder(
                LabelId.VERIFIED, value(1, "Verified"), value(0, "No score"), value(-1, "Fails"))
            .setFunction(LabelFunction.MAX_WITH_BLOCK)
            .build();
    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      ProjectConfig cfg = projectConfigFactory.create(project);
      cfg.load(md);
      cfg.upsertLabelType(verified);
      cfg.commit(md);
    }
    projectCache.evictAndReindex(project);

    String heads = RefNames.REFS_HEADS + "*";
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel(verified.getName()).ref(heads).group(REGISTERED_USERS).range(-1, 1))
        .update();
  }

  private ChangeApi forChange(PushOneCommit.Result r) throws RestApiException {
    return gApi.changes().id(r.getChangeId());
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
    merge(
        createChange(
            testRepo,
            "master",
            "Add OWNER file",
            "OWNERS",
            String.format(
                "inherited: %s\nmatchers:\n" + "- suffix: %s\n  owners:\n%s",
                inherit,
                extension,
                Stream.of(users)
                    .map(user -> String.format("   - %s\n", user.email()))
                    .collect(joining())),
            ""));
  }

  private void addOwnerFileToRoot(boolean inherit, TestAccount u) throws Exception {
    // Add OWNERS file to root:
    //
    // inherited: true
    // owners:
    // - u.email()
    merge(
        createChange(
            testRepo,
            "master",
            "Add OWNER file",
            "OWNERS",
            String.format("inherited: %s\nowners:\n- %s\n", inherit, u.email()),
            ""));
  }
}
