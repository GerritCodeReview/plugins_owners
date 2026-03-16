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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.googlesource.gerrit.owners.AlreadyApprovedByOperand.FULL_OPERAND_WITH_PLUGIN_NAME;
import static java.util.stream.Collectors.joining;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.eclipse.jgit.lib.ObjectId;
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
  private TestAccount NON_OWNER;

  private static final String FRONTEND_OWNED_FILE = "foo.js";
  private static final String BACKEND_OWNED_FILE = "foo.java";
  private static final String FILE_WITH_NO_OWNERS = "foo.txt";

  private static final String FILE_CONTENT =
      IntStream.rangeClosed(1, 10)
          .mapToObj(number -> String.format("Line %d\n", number))
          .collect(joining());

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
        .add(allow(Permission.REBASE).ref("refs/*").group(REGISTERED_USERS))
        .update();

    FRONTEND_FILES_OWNER = accountCreator.create("user-frontend");
    BACKEND_FILES_OWNER = accountCreator.create("user-backend");
    NON_OWNER = accountCreator.create("user-non-owner");

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
  public void shouldCopyApprovalWhenOnlyCommitMessageChangesInNextPatchSet() throws Exception {
    String ownedFile = "file.txt";
    pushOwnersToMaster(
        String.format("inherited: true\nowners:\n- %s\n", BACKEND_FILES_OWNER.username()));

    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .file(ownedFile)
            .content("java content")
            .create();

    vote(BACKEND_FILES_OWNER, changeId.toString(), 2);

    changeOperations
        .change(changeId)
        .newPatchset()
        .commitMessage("Updated commit message")
        .create();

    ChangeInfo c = detailedChange(changeId.toString());

    assertVotes(c, BACKEND_FILES_OWNER, 2);
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

  @Test
  public void shouldCopyApprovalWhenAllEditsToOwnedFileAreDueToRebase() throws Exception {
    ObjectId initialCommitId = createInitialContentFor(BACKEND_OWNED_FILE);
    PushOneCommit.Result amendL3 =
        createChangeWithReplacedContent(BACKEND_OWNED_FILE, "Line 3\n", "Line three\n");
    vote(BACKEND_FILES_OWNER, amendL3.getChangeId(), 2);

    testRepo.reset(initialCommitId);
    PushOneCommit.Result amendL7 =
        createChangeWithReplacedContent(BACKEND_OWNED_FILE, "Line 7\n", "Line seven\n");

    rebaseChangeOn(amendL3.getChangeId(), amendL7.getCommit().getId());

    ChangeInfo c = detailedChange(amendL3.getChangeId());
    assertVotes(c, BACKEND_FILES_OWNER, 2);
  }

  @Test
  public void
      shouldCopyApprovalWhenAllEditsToOwnedFileAreDueToRebaseEvenIfAnUnrelatedFileWasAddedByTheNewBase()
          throws Exception {
    ObjectId initialCommitId = createInitialContentFor(FRONTEND_OWNED_FILE);
    PushOneCommit.Result amendL3 =
        createChangeWithReplacedContent(FRONTEND_OWNED_FILE, "Line 3\n", "Line three\n");
    vote(FRONTEND_FILES_OWNER, amendL3.getChangeId(), 2);

    testRepo.reset(initialCommitId);
    Change.Id baseChangeWithUnrelatedFiles =
        changeOperations
            .newChange()
            .project(project)
            .file(FRONTEND_OWNED_FILE)
            .content(FILE_CONTENT.replace("Line 7\n", "Line seven\n"))
            .file("unrelated.txt")
            .content("unrelated change")
            .create();

    rebaseChangeOn(amendL3.getChangeId(), commitOf(baseChangeWithUnrelatedFiles));

    vote(FRONTEND_FILES_OWNER, amendL3.getChangeId(), 2);
  }

  @Test
  public void
      shouldCopyApprovalWhenAllEditsToOwnedFileAreDueToRebaseEvenIfAnUnrelatedFileWasAddedByTheRebasedChange()
          throws Exception {
    ObjectId initialCommitId = createInitialContentFor(BACKEND_OWNED_FILE);
    PushOneCommit.Result amendL3 =
        createChangeWithReplacedContent(BACKEND_OWNED_FILE, "Line 3\n", "Line three\n");
    vote(BACKEND_FILES_OWNER, amendL3.getChangeId(), 2);

    testRepo.reset(initialCommitId);
    PushOneCommit.Result amendL7 =
        createChangeWithReplacedContent(BACKEND_OWNED_FILE, "Line 7\n", "Line seven\n");

    // Rebase and include an unrelated file
    testRepo.reset(amendL7.getCommit().getId());
    testRepo.cherryPick(amendL3.getCommit());
    PushOneCommit.Result rebasedL30WithUnrelatedChanges =
        amendChange(
            amendL3.getChangeId(), "Rebased with changes", "unrelated.txt", "Unrelated change");
    rebasedL30WithUnrelatedChanges.assertOkStatus();

    ChangeInfo c = detailedChange(amendL3.getChangeId());
    assertVotes(c, BACKEND_FILES_OWNER, 2);
  }

  @Test
  public void
      shouldNotCopyApprovalWhenAllEditsToOwnedFileAreDueToRebaseButAnotherOwnedFileWasAdded()
          throws Exception {
    ObjectId initialCommitId = createInitialContentFor(FRONTEND_OWNED_FILE);
    PushOneCommit.Result amendL3 =
        createChangeWithReplacedContent(FRONTEND_OWNED_FILE, "Line 3\n", "Line three\n");
    vote(FRONTEND_FILES_OWNER, amendL3.getChangeId(), 2);

    testRepo.reset(initialCommitId);
    PushOneCommit.Result amendL7 =
        createChangeWithReplacedContent(FRONTEND_OWNED_FILE, "Line 7\n", "Line seven\n");

    testRepo.reset(amendL7.getCommit().getId());
    testRepo.cherryPick(amendL3.getCommit());
    PushOneCommit.Result rebasedL30PlusOtherChangesToOwnedFile =
        amendChange(
            amendL3.getChangeId(),
            "Rebased + Add another owned file",
            "another-owned.js",
            "Line 1\n");
    rebasedL30PlusOtherChangesToOwnedFile.assertOkStatus();

    ChangeInfo c = detailedChange(amendL3.getChangeId());
    assertVotes(c, FRONTEND_FILES_OWNER, 0);
  }

  @Test
  public void shouldNotCopyApprovalWhenRebasingButFurtherAmending() throws Exception {
    ObjectId initialCommitId = createInitialContentFor(BACKEND_OWNED_FILE);
    PushOneCommit.Result amendL3 =
        createChangeWithReplacedContent(BACKEND_OWNED_FILE, "Line 3\n", "Line three\n");
    vote(BACKEND_FILES_OWNER, amendL3.getChangeId(), 2);

    testRepo.reset(initialCommitId);
    PushOneCommit.Result amendL7 =
        createChangeWithReplacedContent(BACKEND_OWNED_FILE, "Line 7\n", "Line seven\n");

    // Rebase and include an unrelated file
    testRepo.reset(amendL7.getCommit().getId());
    testRepo.cherryPick(amendL3.getCommit());
    PushOneCommit.Result rebasedL3WithUnrelatedChanges =
        amendChange(
            amendL3.getChangeId(),
            "Rebased with additional change to Line 5",
            BACKEND_OWNED_FILE,
            FILE_CONTENT.replace("Line 5\n", "Line five\n"));
    rebasedL3WithUnrelatedChanges.assertOkStatus();

    ChangeInfo c = detailedChange(amendL3.getChangeId());
    assertVotes(c, BACKEND_FILES_OWNER, 0);
  }

  @Test
  public void shouldCopyApprovalWhenInheritingARemovalOfAnOwnedFileFromRebase() throws Exception {
    createInitialContentFor(BACKEND_OWNED_FILE);
    ObjectId latestMasterCommitId = createInitialContentFor("another-owned.java");

    Change.Id removedFileChangeId =
        changeOperations.newChange().project(project).file("another-owned.java").delete().create();

    vote(BACKEND_FILES_OWNER, removedFileChangeId.toString(), 2);

    testRepo.reset(latestMasterCommitId);
    Change.Id baseRemovedFileChangeId =
        changeOperations.newChange().project(project).file(BACKEND_OWNED_FILE).delete().create();

    rebaseChangeOn(removedFileChangeId.toString(), commitOf(baseRemovedFileChangeId));

    ChangeInfo c = detailedChange(removedFileChangeId.toString());
    assertVotes(c, BACKEND_FILES_OWNER, 2);
  }

  @Test
  public void shouldCopyApprovalWhenRemovingOwnedFileAndRebaseOnChangeThatRemovesTheSameFile()
      throws Exception {
    ObjectId initialCommitId = createInitialContentFor(BACKEND_OWNED_FILE);
    Change.Id removedFileChangeId =
        changeOperations.newChange().project(project).file(BACKEND_OWNED_FILE).delete().create();

    vote(BACKEND_FILES_OWNER, removedFileChangeId.toString(), 2);

    testRepo.reset(initialCommitId);
    Change.Id baseRemovedFileChangeId =
        changeOperations.newChange().project(project).file(BACKEND_OWNED_FILE).delete().create();

    rebaseChangeOn(removedFileChangeId.toString(), commitOf(baseRemovedFileChangeId));

    ChangeInfo c = detailedChange(removedFileChangeId.toString());
    assertVotes(c, BACKEND_FILES_OWNER, 2);
  }

  @Test
  public void shouldCopyApprovalWhenAllModifiedFilesAreOwnedAndAutoOwnersApprovedIsDefault()
      throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .owner(BACKEND_FILES_OWNER.id())
            .file(BACKEND_OWNED_FILE)
            .content("java content")
            .create();

    vote(BACKEND_FILES_OWNER, changeId.toString(), 2);

    changeOperations
        .change(changeId)
        .newPatchset()
        .uploader(BACKEND_FILES_OWNER.id())
        .file(BACKEND_OWNED_FILE)
        .content("updated java content")
        .create();

    ChangeInfo c = detailedChange(changeId.toString());
    assertVotes(c, BACKEND_FILES_OWNER, 2);
  }

  @Test
  public void shouldNotCopyApprovalWhenAllModifiedFilesAreOwnedButApproverIsNotChangeOwner()
      throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .owner(NON_OWNER.id())
            .file(BACKEND_OWNED_FILE)
            .content("java content")
            .create();

    vote(BACKEND_FILES_OWNER, changeId.toString(), 2);

    changeOperations
        .change(changeId)
        .newPatchset()
        .uploader(BACKEND_FILES_OWNER.id())
        .file(BACKEND_OWNED_FILE)
        .content("updated java content")
        .create();

    ChangeInfo c = detailedChange(changeId.toString());
    assertVotes(c, BACKEND_FILES_OWNER, 0);
  }

  @Test
  public void shouldNotCopyApprovalWhenAllModifiedFilesAreOwnedButUploaderNotOwner()
      throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .owner(BACKEND_FILES_OWNER.id())
            .file(BACKEND_OWNED_FILE)
            .content("java content")
            .create();

    vote(BACKEND_FILES_OWNER, changeId.toString(), 2);

    changeOperations
        .change(changeId)
        .newPatchset()
        .uploader(NON_OWNER.id())
        .file(BACKEND_OWNED_FILE)
        .content("updated java content")
        .create();

    ChangeInfo c = detailedChange(changeId.toString());
    assertVotes(c, BACKEND_FILES_OWNER, 0);
  }

  @Test
  public void shouldNotCopyApprovalWhenAllModifiedFilesAreOwnedButAutoOwnersApprovedIsFalse()
      throws Exception {
    pushOwnersToMaster(
        String.format(
            "inherited: true\nauto-owners-approved: false\nowners:\n- %s\n",
            BACKEND_FILES_OWNER.username()));

    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .owner(BACKEND_FILES_OWNER.id())
            .file(BACKEND_OWNED_FILE)
            .content("java content")
            .create();

    vote(BACKEND_FILES_OWNER, changeId.toString(), 2);

    changeOperations
        .change(changeId)
        .newPatchset()
        .uploader(BACKEND_FILES_OWNER.id())
        .file(BACKEND_OWNED_FILE)
        .content("updated java content")
        .create();

    ChangeInfo c = detailedChange(changeId.toString());
    assertVotes(c, BACKEND_FILES_OWNER, 0);
  }

  @Test
  public void shouldCopyApprovalWhenAllModifiedFilesAreOwnedAndAutoOwnersApprovedIsTrue()
      throws Exception {
    pushOwnersToMaster(
        String.format(
            "inherited: true\nauto-owners-approved: true\nowners:\n- %s\n",
            BACKEND_FILES_OWNER.username()));

    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .owner(BACKEND_FILES_OWNER.id())
            .file(BACKEND_OWNED_FILE)
            .content("java content")
            .create();

    vote(BACKEND_FILES_OWNER, changeId.toString(), 2);

    changeOperations
        .change(changeId)
        .newPatchset()
        .uploader(BACKEND_FILES_OWNER.id())
        .file(BACKEND_OWNED_FILE)
        .content("updated java content")
        .create();

    ChangeInfo c = detailedChange(changeId.toString());
    assertVotes(c, BACKEND_FILES_OWNER, 2);
  }

  @Test
  public void shouldCopyApprovalWhenAutoOwnersApprovedIsFalseButOwnedEditsAreRebaseOnly()
      throws Exception {
    pushOwnersToMaster(
        String.format(
            "inherited: true\nauto-owners-approved: false\nowners:\n- %s\n",
            BACKEND_FILES_OWNER.username()));

    ObjectId initialCommitId = createInitialContentFor(BACKEND_OWNED_FILE);
    PushOneCommit.Result amendL3 =
        createChangeWithReplacedContent(BACKEND_OWNED_FILE, "Line 3\n", "Line three\n");
    vote(BACKEND_FILES_OWNER, amendL3.getChangeId(), 2);

    testRepo.reset(initialCommitId);
    PushOneCommit.Result amendL7 =
        createChangeWithReplacedContent(BACKEND_OWNED_FILE, "Line 7\n", "Line seven\n");

    rebaseChangeOn(amendL3.getChangeId(), amendL7.getCommit().getId());

    ChangeInfo c = detailedChange(amendL3.getChangeId());
    assertVotes(c, BACKEND_FILES_OWNER, 2);
  }

  @Test
  public void shouldNotCopyApprovalWhenChangedFilesAreNotOwnedByUploader() throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .owner(BACKEND_FILES_OWNER.id())
            .file(BACKEND_OWNED_FILE)
            .content("java content")
            .create();

    vote(BACKEND_FILES_OWNER, changeId.toString(), 2);

    changeOperations
        .change(changeId)
        .newPatchset()
        .uploader(BACKEND_FILES_OWNER.id())
        .file(BACKEND_OWNED_FILE)
        .content("updated java content")
        .file(FILE_WITH_NO_OWNERS)
        .content("updated text")
        .create();

    ChangeInfo c = detailedChange(changeId.toString());
    assertVotes(c, BACKEND_FILES_OWNER, 0);
  }

  private PushOneCommit.Result createChangeWithReplacedContent(
      String file, String oldLine, String replacement) throws Exception {
    PushOneCommit.Result r =
        createChange(
            String.format("Replaced '%s' with '%s'", oldLine, replacement),
            file,
            FILE_CONTENT.replace(oldLine, replacement));
    r.assertOkStatus();
    return r;
  }

  private ObjectId createInitialContentFor(String fileName) throws Exception {
    PushOneCommit.Result initial =
        createCommitAndPush(
            testRepo, "refs/heads/master", "Create base file", fileName, FILE_CONTENT);
    initial.assertOkStatus();
    return initial.getCommit().getId();
  }

  private ObjectId commitOf(Change.Id changeId) throws Exception {
    RevisionInfo currentRevision =
        gApi.changes().id(project.get(), changeId.get()).get().getCurrentRevision();
    return ObjectId.fromString(currentRevision.commit.commit);
  }

  private void rebaseChangeOn(String changeId, ObjectId newParent) throws Exception {
    RebaseInput rebaseInput = new RebaseInput();
    rebaseInput.base = newParent.getName();
    gApi.changes().id(changeId).current().rebase(rebaseInput);
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

  private void addOwnerFileWithMatchersToRoot(
      Map<String, List<TestAccount>> ownersBySuffix, boolean autoOwnersApproved) throws Exception {
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

    pushOwnersToMaster(
        String.format(
            "inherited: %s\nauto-owners-approved: %s\nmatchers:\n%s",
            true, autoOwnersApproved, matchersYaml));
  }

  private void pushOwnersToMaster(String owners) throws Exception {
    pushFactory
        .create(admin.newIdent(), testRepo, "Add OWNER file", "OWNERS", owners)
        .to(RefNames.fullName("master"))
        .assertOkStatus();
  }
}
