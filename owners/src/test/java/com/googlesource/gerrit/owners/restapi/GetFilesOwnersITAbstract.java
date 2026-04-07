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

package com.googlesource.gerrit.owners.restapi;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.googlesource.gerrit.owners.AlreadyApprovedByOperand.FULL_OPERAND_WITH_PLUGIN_NAME;

import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GlobalPluginConfig;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.inject.Inject;
import com.googlesource.gerrit.owners.common.InvalidOwnersFileException;
import com.googlesource.gerrit.owners.common.LabelDefinition;
import com.googlesource.gerrit.owners.entities.FilesOwnersResponse;
import com.googlesource.gerrit.owners.entities.GroupOwner;
import com.googlesource.gerrit.owners.entities.Owner;
import com.googlesource.gerrit.owners.restapi.GetFilesOwners.LabelNotFoundException;
import java.util.Map;
import java.util.function.Consumer;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.transport.FetchResult;
import org.junit.Test;

public abstract class GetFilesOwnersITAbstract extends LightweightPluginDaemonTest {

  private static final String REFS_META_CONFIG = RefNames.REFS_META + "config";
  private static final String AUTO_APPROVED_FILE = "foo.java";

  @Inject protected ProjectOperations projectOperations;
  @Inject protected RequestScopeOperations requestScopeOperations;
  @Inject protected ChangeOperations changeOperations;

  protected GetFilesOwners ownersApi;
  private Owner rootOwner;
  private Owner projectOwner;
  private NameKey parentProjectName;
  private NameKey childProjectName;
  private TestRepository<InMemoryRepository> childRepo;
  private TestRepository<InMemoryRepository> parentRepo;
  private TestRepository<InMemoryRepository> allProjectsRepo;

  @Override
  public void setUpTestPlugin() throws Exception {
    super.setUpTestPlugin();

    rootOwner = new Owner(admin.fullName(), admin.id().get());
    projectOwner = new Owner(user.fullName(), user.id().get());
    ownersApi = plugin.getSysInjector().getInstance(GetFilesOwners.class);

    parentProjectName =
        createProjectOverAPI("parent", allProjects, true, SubmitType.FAST_FORWARD_ONLY);
    parentRepo = cloneProjectWithMetaRefs(parentProjectName);

    childProjectName =
        createProjectOverAPI("child", parentProjectName, true, SubmitType.FAST_FORWARD_ONLY);
    childRepo = cloneProject(childProjectName);

    allProjectsRepo = cloneProjectWithMetaRefs(allProjects);
  }

  @Test
  public void shouldReturnExactFileOwners() throws Exception {
    addOwnerFileToRoot(true);
    assertChangeHasOwners(createChange().getChangeId());
  }

  @Test
  public void shouldReturnExactFileOwnersWhenOwnersIsSetToAllProjects() throws Exception {
    addOwnerFileWithMatchers(allProjectsRepo, REFS_META_CONFIG, true);
    assertChangeHasOwners(createChange(childRepo).getChangeId());
  }

  @Test
  public void shouldReturnExactFileOwnersWhenOwnersIsSetToParentProject() throws Exception {
    addOwnerFileWithMatchers(parentRepo, REFS_META_CONFIG, true);
    assertChangeHasOwners(createChange(childRepo).getChangeId());
  }

  @Test
  public void shouldReturnEmptyOwnersLabelsWhenNotApprovedByOwners() throws Exception {
    addOwnerFileToRoot(true);
    String changeId = createChange().getChangeId();

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files)
        .containsExactly("a.txt", Sets.newHashSet(new Owner(admin.fullName(), admin.id().get())));

    assertThat(resp.value().ownersLabels).isEmpty();
  }

  @Test
  public void shouldReturnNonEmptyOwnersLabelsWhenApprovedByOwners() throws Exception {
    addOwnerFileToRoot(true);
    String changeId = createChange().getChangeId();
    approve(changeId);

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().ownersLabels)
        .containsExactly(admin.id().get(), Map.of(LabelId.CODE_REVIEW, 2));
  }

  @Test
  public void shouldReturnEmptyFilesAndNonEmptyFilesApprovedResponseWhenApprovedByOwners()
      throws Exception {
    addOwnerFileToRoot(true);
    String changeId = createChange().getChangeId();
    approve(changeId);

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files).isEmpty();
    assertThat(resp.value().filesApproved)
        .containsExactly("a.txt", Sets.newHashSet(new Owner(admin.fullName(), admin.id().get())));
    assertThat(resp.value().filesAutoApproved).isEmpty();
  }

  @Test
  public void shouldReturnFilesAutoApprovedWhenOwnerVoteIsCopied() throws Exception {
    TestAccount autoApprovalOwner = accountCreator.create("user-backend");
    setupAutoApprovalFor(autoApprovalOwner);

    Change.Id changeId = createChangeWithCopiedOwnerVote(autoApprovalOwner);

    Response<FilesOwnersResponse> response =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId.toString())));

    assertThat(response.value().files).isEmpty();
    assertThat(response.value().filesApproved)
        .containsExactly(
            AUTO_APPROVED_FILE,
            Sets.newHashSet(new Owner(autoApprovalOwner.fullName(), autoApprovalOwner.id().get())));
    assertThat(response.value().filesAutoApproved)
        .containsExactly(
            AUTO_APPROVED_FILE,
            Sets.newHashSet(new Owner(autoApprovalOwner.fullName(), autoApprovalOwner.id().get())));
  }

  @Test
  public void shouldNotReturnFilesAutoApprovedWhenAnOwnerExplicitlyVotesOnCurrentPatchSet()
      throws Exception {
    TestAccount copiedVoteOwner = accountCreator.create("user-backend");
    TestAccount explicitVoteOwner = accountCreator.create("user-backend-2");
    setupAutoApprovalFor(copiedVoteOwner, explicitVoteOwner);

    Change.Id changeId = createChangeWithCopiedOwnerVote(copiedVoteOwner);

    vote(explicitVoteOwner, changeId.toString(), 2);

    Response<FilesOwnersResponse> response =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId.toString())));

    assertThat(response.value().files).isEmpty();
    assertThat(response.value().filesApproved)
        .containsExactly(
            AUTO_APPROVED_FILE,
            Sets.newHashSet(
                new Owner(copiedVoteOwner.fullName(), copiedVoteOwner.id().get()),
                new Owner(explicitVoteOwner.fullName(), explicitVoteOwner.id().get())));
    assertThat(response.value().filesAutoApproved).isEmpty();
  }

  @Test
  public void shouldNotReturnFilesAutoApprovedWhenCopiedVoteUsesLegacyOwnersLogic()
      throws Exception {
    addOwnerFileWithMatchersToRoot(true);
    updateLabel(b -> b.setCopyCondition("approverin:" + FULL_OPERAND_WITH_PLUGIN_NAME));

    String changeId = createChange().getChangeId();
    approve(changeId);
    Change.Id numericChangeId = parseCurrentRevisionResource(changeId).getChange().getId();

    changeOperations.change(numericChangeId).newPatchset().file("foo.java").content("bar").create();

    Response<FilesOwnersResponse> response =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(response.value().filesApproved)
        .containsExactly("a.txt", Sets.newHashSet(new Owner(admin.fullName(), admin.id().get())));
    assertThat(response.value().filesAutoApproved).isEmpty();
  }

  @Test
  @GlobalPluginConfig(pluginName = "owners", name = "owners.expandGroups", value = "false")
  public void shouldReturnResponseWithUnexpandedFileOwners() throws Exception {
    addOwnerFileToRoot(true);
    String changeId = createChange().getChangeId();

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files)
        .containsExactly("a.txt", Sets.newHashSet(new GroupOwner(admin.username())));
    assertThat(resp.value().filesApproved).isEmpty();
  }

  @Test
  @GlobalPluginConfig(pluginName = "owners", name = "owners.expandGroups", value = "false")
  public void
      shouldReturnEmptyFilesAndNonEmptyFilesApprovedResponseWhenApprovedByOwnersWithUnexpandedFileOwners()
          throws Exception {
    addOwnerFileToRoot(true);
    String changeId = createChange().getChangeId();
    approve(changeId);

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files).isEmpty();
    assertThat(resp.value().filesApproved)
        .containsExactly("a.txt", Sets.newHashSet(new GroupOwner(admin.username())));
  }

  @Test
  @GlobalPluginConfig(pluginName = "owners", name = "owners.expandGroups", value = "false")
  public void shouldReturnResponseWithUnexpandedFileMatchersOwners() throws Exception {
    addOwnerFileWithMatchersToRoot(true);
    String changeId = createChange().getChangeId();

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files)
        .containsExactly("a.txt", Sets.newHashSet(new GroupOwner(admin.username())));
    assertThat(resp.value().filesApproved).isEmpty();
  }

  @Test
  public void shouldNotApproveOwnedFilesWhenCustomLabelInOwnersFileNotProvided() throws Exception {
    addOwnerFileToRootWithLabel(LabelDefinition.parse("Foo,1").get(), admin);
    String changeId = createChange().getChangeId();
    approve(changeId);

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files)
        .containsExactly("a.txt", Sets.newHashSet(new Owner(admin.fullName(), admin.id().get())));
    assertThat(resp.value().filesApproved).isEmpty();
  }

  @Test
  @UseLocalDisk
  public void shouldReturnInheritedOwnersFromProjectsOwners() throws Exception {
    assertInheritFromProject(project);
  }

  @Test
  @UseLocalDisk
  public void shouldReturnInheritedOwnersFromParentProjectsOwners() throws Exception {
    assertInheritFromProject(allProjects);
  }

  @Test
  @UseLocalDisk
  public void shouldReflectChangesInParentProject() throws Exception {
    addOwnerFileToProjectConfig(allProjects, true, admin);

    String changeId = createChange().getChangeId();
    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));
    assertThat(resp.value().files).containsExactly("a.txt", Sets.newHashSet(rootOwner));

    addOwnerFileToProjectConfig(allProjects, true, user);
    resp = assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));
    assertThat(resp.value().files).containsExactly("a.txt", Sets.newHashSet(projectOwner));
    assertThat(resp.value().filesApproved).isEmpty();
  }

  @Test
  @UseLocalDisk
  public void shouldNotReturnInheritedOwnersFromProjectsOwners() throws Exception {
    assertNotInheritFromProject(project);
  }

  @Test
  @UseLocalDisk
  public void shouldNotReturnInheritedOwnersFromParentProjectsOwners() throws Exception {
    addOwnerFileToProjectConfig(project, false);
    assertNotInheritFromProject(allProjects);
  }

  @Test
  @UseLocalDisk
  public void shouldThrowExceptionWhenCodeReviewLabelIsNotConfigured() throws Exception {
    addOwnerFileToProjectConfig(project, false);
    replaceCodeReviewWithLabel(
        TestLabels.label(
            "Foo", TestLabels.value(1, "Foo is fine"), TestLabels.value(-1, "Foo is not fine")));
    String changeId = createChange().getChangeId();

    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () -> ownersApi.apply(parseCurrentRevisionResource(changeId)));
    assertThat(thrown).hasMessageThat().isEqualTo(GetFilesOwners.MISSING_CODE_REVIEW_LABEL);
    assertThat(thrown).hasCauseThat().isInstanceOf(LabelNotFoundException.class);
  }

  @Test
  @UseLocalDisk
  public void shouldThrowResourceConflictWhenOwnersFileIsBroken() throws Exception {
    addBrokenOwnersFileToRoot();
    String changeId = createChange().getChangeId();

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> ownersApi.apply(parseCurrentRevisionResource(changeId)));
    assertThat(thrown).hasMessageThat().startsWith("Invalid owners file: OWNERS");
    assertThat(thrown).hasCauseThat().isInstanceOf(InvalidOwnersFileException.class);
  }

  protected void replaceCodeReviewWithLabel(LabelType label) throws Exception {
    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      u.getConfig().getLabelSections().remove(LabelId.CODE_REVIEW);
      u.getConfig().upsertLabelType(label);
      u.save();
    }
  }

  protected static <T> Response<T> assertResponseOk(Response<T> response) {
    assertThat(response.statusCode()).isEqualTo(HttpServletResponse.SC_OK);
    return response;
  }

  protected void vote(TestAccount voter, String changeId, int value) throws Exception {
    requestScopeOperations.setApiUser(voter.id());
    try {
      gApi.changes()
          .id(changeId)
          .current()
          .review(new ReviewInput().label(LabelId.CODE_REVIEW, value));
    } finally {
      requestScopeOperations.setApiUser(admin.id());
    }
  }

  private Change.Id createChangeWithCopiedOwnerVote(TestAccount owner) throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .owner(owner.id())
            .file(AUTO_APPROVED_FILE)
            .content("first version")
            .create();

    vote(owner, changeId.toString(), 2);

    changeOperations
        .change(changeId)
        .newPatchset()
        .uploader(owner.id())
        .file(AUTO_APPROVED_FILE)
        .content("second version")
        .create();
    return changeId;
  }

  private void setupAutoApprovalFor(TestAccount... owners) throws Exception {
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .update();

    pushOwnersToMaster(
        String.format(
            "inherited: true\nauto-owners-approved: true\nowners:\n%s",
            java.util.Arrays.stream(owners)
                .map(owner -> String.format("- %s\n", owner.username()))
                .collect(java.util.stream.Collectors.joining())));
    updateLabel(b -> b.setCopyCondition("approverin:" + FULL_OPERAND_WITH_PLUGIN_NAME));
  }

  private void assertNotInheritFromProject(Project.NameKey projectNameKey) throws Exception {
    addOwnerFileToRoot(false);
    addOwnerFileToProjectConfig(projectNameKey, true);

    String changeId = createChange().getChangeId();
    assertChangeHasOwners(changeId);
  }

  private void assertChangeHasOwners(String changeId)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files).containsExactly("a.txt", Sets.newHashSet(rootOwner));
    assertThat(resp.value().filesApproved).isEmpty();
  }

  private void assertInheritFromProject(Project.NameKey projectNameKey) throws Exception {
    addOwnerFileToRoot(true);
    addOwnerFileToProjectConfig(projectNameKey, true);

    String changeId = createChange().getChangeId();
    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files)
        .containsExactly("a.txt", Sets.newHashSet(rootOwner, projectOwner));
    assertThat(resp.value().filesApproved).isEmpty();
  }

  private void addBrokenOwnersFileToRoot() throws Exception {
    merge(createChange(testRepo, "master", "Add OWNER file", "OWNERS", "{foo", ""));
  }

  private void addOwnerFileToProjectConfig(Project.NameKey projectNameKey, boolean inherit)
      throws Exception {
    addOwnerFileToProjectConfig(projectNameKey, inherit, user);
  }

  private void addOwnerFileToProjectConfig(
      Project.NameKey projectNameKey, boolean inherit, TestAccount account) throws Exception {
    TestRepository<InMemoryRepository> project = cloneProject(projectNameKey);
    GitUtil.fetch(project, RefNames.REFS_CONFIG + ":" + RefNames.REFS_CONFIG);
    project.reset(RefNames.REFS_CONFIG);
    pushFactory
        .create(
            admin.newIdent(),
            project,
            "Add OWNER file",
            "OWNERS",
            String.format(
                "inherited: %s\nmatchers:\n" + "- suffix: .txt\n  owners:\n   - %s\n",
                inherit, account.email()))
        .to(RefNames.REFS_CONFIG);
  }

  private void addOwnerFileToRoot(boolean inherit) throws Exception {
    // Add OWNERS file to root:
    //
    // inherited: true
    // owners:
    // - admin
    merge(
        createChange(
            testRepo,
            "master",
            "Add OWNER file",
            "OWNERS",
            String.format("inherited: %s\nowners:\n- %s\n", inherit, admin.email()),
            ""));
  }

  protected void addOwnerFileToRootWithLabel(LabelDefinition label, TestAccount u)
      throws Exception {
    // Add OWNERS file to root:
    //
    // inherited: true
    // label: label,score # score is optional
    // owners:
    // - u.email()
    String owners =
        String.format(
            "inherited: true\nlabel: %s\nowners:\n- %s\n",
            String.format(
                "%s%s",
                label.getName(),
                label.getScore().map(value -> String.format(",%d", value)).orElse("")),
            u.email());
    pushFactory
        .create(admin.newIdent(), testRepo, "Add OWNER file", "OWNERS", owners)
        .to(RefNames.fullName("master"))
        .assertOkStatus();
  }

  protected void updateLabel(Consumer<LabelType.Builder> update) throws Exception {
    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, update);
      u.save();
    }
  }

  private void addOwnerFileWithMatchersToRoot(boolean inherit) throws Exception {
    addOwnerFileWithMatchers(testRepo, "master", inherit);
  }

  private void addOwnerFileWithMatchers(TestRepository<?> repo, String targetRef, boolean inherit)
      throws Exception {
    // Add OWNERS file to root:
    //
    // inherited: true
    // matchers:
    // - suffix: .txt
    //   owners:
    //   - admin@mail.com
    Result changeCreated =
        createChange(
            repo,
            targetRef,
            "Add OWNER file",
            "OWNERS",
            String.format(
                "inherited: %s\nmatchers:\n" + "- suffix: .txt\n  owners:\n   - %s\n",
                inherit, admin.email()),
            "");
    changeCreated.assertOkStatus();
    merge(changeCreated);
  }

  private void pushOwnersToMaster(String owners) throws Exception {
    pushFactory
        .create(admin.newIdent(), testRepo, "Add OWNERS file", "OWNERS", owners)
        .to(RefNames.fullName("master"))
        .assertOkStatus();
  }

  public TestRepository<InMemoryRepository> cloneProjectWithMetaRefs(Project.NameKey project)
      throws Exception {
    TestRepository<InMemoryRepository> clonedProject = cloneProject(project);
    String initialRef = "refs/remotes/origin/config";
    FetchResult result =
        clonedProject
            .git()
            .fetch()
            .setRemote("origin")
            .setRefSpecs("+refs/meta/config:refs/remotes/origin/config")
            .call();
    if (result.getTrackingRefUpdate(initialRef) != null) {
      clonedProject.reset(initialRef);
    }
    return clonedProject;
  }
}
