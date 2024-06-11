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

import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.common.SubmitRecordInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import com.googlesource.gerrit.owners.common.LabelDefinition;
import java.util.Collection;
import java.util.stream.Stream;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;

abstract class OwnersSubmitRequirementITAbstract extends LightweightPluginDaemonTest {
  @Inject private ProjectOperations projectOperations;

  protected void verifyHasSubmitRecord(
      Collection<SubmitRecordInfo> records, String label, SubmitRecordInfo.Label.Status status) {
    assertThat(
            records.stream()
                .flatMap(record -> record.labels.stream())
                .filter(l -> l.label.equals(label) && l.status == status)
                .findAny())
        .isPresent();
  }

  protected void installVerifiedLabel() throws Exception {
    installLabel(LabelId.VERIFIED);
  }

  protected void installLabel(String labelId) throws Exception {
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

  protected void installLabel(LabelType label) throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().upsertLabelType(label);
      u.save();
    }
  }

  protected ChangeApi forChange(PushOneCommit.Result r) throws RestApiException {
    return gApi.changes().id(r.getChangeId());
  }

  protected void addBrokenOwnersFileToRoot() throws Exception {
    pushOwnersToMaster("{foo");
  }

  protected void addOwnerFileWithMatchersToRoot(
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

  protected void addOwnerFileToRoot(boolean inherit, TestAccount u) throws Exception {
    // Add OWNERS file to root:
    //
    // inherited: true
    // owners:
    // - u.email()
    pushOwnersToMaster(String.format("inherited: %s\nowners:\n- %s\n", inherit, u.email()));
  }

  protected void addOwnerFileToRefsMetaConfig(
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
