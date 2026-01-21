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
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.googlesource.gerrit.owners.AlreadyApprovedByOperand.FULL_OPERAND_WITH_PLUGIN_NAME;
import static java.util.stream.Collectors.joining;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;

@TestPlugin(name = "owners", sysModule = "com.googlesource.gerrit.owners.OwnersModule")
@UseLocalDisk
public class AlreadyApprovedByCopyConditionIT extends LightweightPluginDaemonTest {
  @Inject protected RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private ChangeOperations changeOperations;

  private TestAccount FRONTEND_FILES_OWNER;
  private TestAccount BACKEND_FILES_OWNER;

  private static final String FRONTEND_OWNED_FILE = "foo.js";
  private static final String BACKEND_OWNED_FILE = "foo.java";
  private static final String FILE_WITH_NO_OWNERS = "foo.txt";

  @Before
  public void setup() throws Exception {
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .update();

    FRONTEND_FILES_OWNER = accountCreator.create("user-frontend");
    BACKEND_FILES_OWNER = accountCreator.create("user-backend");

    addOwnerFileWithMatchersToRoot(
        Map.of(
            ".js", List.of(FRONTEND_FILES_OWNER),
            ".java", List.of(BACKEND_FILES_OWNER)));

    updateLabel(b -> b.setCopyCondition("approverin:" + FULL_OPERAND_WITH_PLUGIN_NAME));
  }

  @Test
  public void shouldCopyOwnerApprovalOnlyWhenOwnedFilesAreUnchanged() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file(FRONTEND_OWNED_FILE)
            .content("some frontend change")
            .create();

    vote(FRONTEND_FILES_OWNER, changeId.toString(), 2);
    vote(BACKEND_FILES_OWNER, changeId.toString(), 2);

    changeOperations
        .change(changeId)
        .newPatchset()
        .file(BACKEND_OWNED_FILE)
        .content("some java content")
        .create();

    ChangeInfo c = detailedChange(changeId.toString());
    assertVotes(c, FRONTEND_FILES_OWNER, 2);
    assertVotes(c, BACKEND_FILES_OWNER, 0);
  }

  @Test
  public void shouldNotCopyApprovalForOwnerWhenNoOwnedFileExists() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file(FILE_WITH_NO_OWNERS)
            .content("file with no owners")
            .create();

    vote(FRONTEND_FILES_OWNER, changeId.toString(), 2);

    changeOperations
        .change(changeId)
        .newPatchset()
        .file(FILE_WITH_NO_OWNERS)
        .content("updated text")
        .create();

    ChangeInfo c = detailedChange(changeId.toString());

    assertVotes(c, FRONTEND_FILES_OWNER, 0);
  }

  @Test
  public void shouldNotCopyApprovalWhenOwnedFilesAreIntroducedInLaterPatchSet() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file(FILE_WITH_NO_OWNERS)
            .content("file with no owners")
            .create();

    vote(BACKEND_FILES_OWNER, changeId.toString(), 2);

    changeOperations
        .change(changeId)
        .newPatchset()
        .file(BACKEND_OWNED_FILE)
        .content("some java content")
        .create();

    ChangeInfo c = detailedChange(changeId.toString());

    assertVotes(c, BACKEND_FILES_OWNER, 0);
  }

  @Test
  public void shouldNotCopyApprovalWhenOwnedFileIsDeleted() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file(BACKEND_OWNED_FILE)
            .content("java content")
            .create();

    vote(BACKEND_FILES_OWNER, changeId.toString(), 2);

    changeOperations.change(changeId).newPatchset().file(BACKEND_OWNED_FILE).delete().create();

    ChangeInfo c = detailedChange(changeId.toString());

    assertVotes(c, BACKEND_FILES_OWNER, 0);
  }

  @Test
  public void shouldNotCopyApprovalWhenOwnedFileIsRenamedToOwnedFile() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file(BACKEND_OWNED_FILE)
            .content("java content")
            .create();

    vote(BACKEND_FILES_OWNER, changeId.toString(), 2);

    changeOperations
        .change(changeId)
        .newPatchset()
        .file(BACKEND_OWNED_FILE)
        .renameTo("renamed-" + BACKEND_OWNED_FILE)
        .create();

    ChangeInfo c = detailedChange(changeId.toString());

    assertVotes(c, BACKEND_FILES_OWNER, 0);
  }

  @Test
  public void shouldNotCopyApprovalWhenOwnedFileIsRenamedToNonOwnedFile() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file(BACKEND_OWNED_FILE)
            .content("java content")
            .create();

    vote(BACKEND_FILES_OWNER, changeId.toString(), 2);

    changeOperations
        .change(changeId)
        .newPatchset()
        .file(BACKEND_OWNED_FILE)
        .renameTo(FILE_WITH_NO_OWNERS)
        .create();

    ChangeInfo c = detailedChange(changeId.toString());

    assertVotes(c, BACKEND_FILES_OWNER, 0);
  }

  private ChangeInfo detailedChange(String changeId) throws Exception {
    return gApi.changes().id(changeId).get(DETAILED_LABELS, CURRENT_REVISION, CURRENT_COMMIT);
  }

  private void assertVotes(ChangeInfo c, TestAccount user, int expectedVote) {
    Integer vote = 0;
    if (c.labels.get(LabelId.CODE_REVIEW) != null
        && c.labels.get(LabelId.CODE_REVIEW).all != null) {
      for (ApprovalInfo approval : c.labels.get(LabelId.CODE_REVIEW).all) {
        if (approval._accountId == user.id().get()) {
          vote = approval.value;
          break;
        }
      }
    }

    assertThat(vote).isEqualTo(expectedVote);
  }

  private void vote(TestAccount user, String changeId, int vote) throws Exception {
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(changeId)
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, vote));
  }

  private void updateLabel(Consumer<LabelType.Builder> update) throws Exception {
    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, update);
      u.save();
    }
  }

  private void addOwnerFileWithMatchersToRoot(Map<String, List<TestAccount>> ownersBySuffix)
      throws Exception {
    // Example generated OWNERS:
    //
    // inherited: true
    // matchers:
    // - suffix: .js
    //   owners:
    //   - fe1@example.com
    //   - fe2@example.com
    // - suffix: .java
    //   owners:
    //   - be1@example.com
    //   - be2@example.com
    //
    String matchersYaml =
        ownersBySuffix.entrySet().stream()
            .map(
                entry -> {
                  String suffix = entry.getKey();
                  List<TestAccount> users = entry.getValue();
                  String ownersYaml =
                      users.stream()
                          .map(user -> String.format("   - %s\n", user.username()))
                          .collect(joining());
                  return String.format("- suffix: %s\n  owners:\n%s", suffix, ownersYaml);
                })
            .collect(joining());

    pushOwnersToMaster(String.format("inherited: %s\nmatchers:\n%s", true, matchersYaml));
  }

  private void pushOwnersToMaster(String owners) throws Exception {
    pushFactory
        .create(admin.newIdent(), testRepo, "Add OWNER file", "OWNERS", owners)
        .to(RefNames.fullName("master"))
        .assertOkStatus();
  }
}
