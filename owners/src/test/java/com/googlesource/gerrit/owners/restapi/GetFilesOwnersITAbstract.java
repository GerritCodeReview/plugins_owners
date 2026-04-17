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
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.transport.FetchResult;
import org.junit.Test;

public abstract class GetFilesOwnersITAbstract extends LightweightPluginDaemonTest {

  private static final String REFS_META_CONFIG = RefNames.REFS_META + "config";
  private static final String OWNED_TXT_FILE = "a.txt";
  private static final String OWNED_JAVA_FILE = "foo.java";
  private static final Set<GroupOwner> NO_AUTO_APPROVED_OWNERS = Set.of();
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

    assertThat(resp.value().files()).containsExactly("a.txt", owners(admin));

    assertThat(resp.value().ownersLabels()).isEmpty();
  }

  @Test
  public void shouldReturnNonEmptyOwnersLabelsWhenApprovedByOwners() throws Exception {
    addOwnerFileToRoot(true);
    String changeId = createChange().getChangeId();
    approve(changeId);

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().ownersLabels())
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

    assertThat(resp.value().files()).isEmpty();
    assertThat(resp.value().filesApproved()).containsExactly("a.txt", owners(admin));
  }

  @Test
  public void shouldReturnFilesAutoApprovedWhenOwnerVoteIsCopied() throws Exception {
    setupAutoApprovalFor(admin);

    Change.Id changeId = createChangeWithCopiedOwnerVote(admin);

    assertFilesApproval(
        changeId.toString(), OWNED_JAVA_FILE, NO_AUTO_APPROVED_OWNERS, owners(admin));
  }

  @Test
  public void shouldNotReturnFilesAutoApprovedWhenOwnerExplicitlyVotesOnCurrentPatchSet()
      throws Exception {
    setupAutoApprovalFor(admin);

    Change.Id changeId = createChangeWithCopiedOwnerVote(admin);
    vote(admin, changeId.toString(), 2);

    assertFilesApproval(
        changeId.toString(), OWNED_JAVA_FILE, owners(admin), NO_AUTO_APPROVED_OWNERS);
  }

  @Test
  public void shouldNotReturnFilesAutoApprovedWhenAnotherOwnerExplicitlyVotesOnCurrentPatchSet()
      throws Exception {
    TestAccount explicitVoteOwner = accountCreator.create("user-backend");
    allowCodeReviewForRegisteredUsers();
    setupAutoApprovalFor(admin, explicitVoteOwner);

    Change.Id changeId = createChangeWithCopiedOwnerVote(admin);
    vote(explicitVoteOwner, changeId.toString(), 2);

    assertFilesApproval(
        changeId.toString(),
        OWNED_JAVA_FILE,
        owners(admin, explicitVoteOwner),
        NO_AUTO_APPROVED_OWNERS);
  }

  @Test
  public void shouldNotReturnFilesAutoApprovedWhenOwnedAndUnownedFilesAreModifiedTogether()
      throws Exception {
    setupAutoApprovalForJavaMatcher(admin);

    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .owner(admin.id())
            .file(OWNED_JAVA_FILE)
            .content("v1")
            .create();
    vote(admin, changeId.toString(), 2);
    changeOperations
        .change(changeId)
        .newPatchset()
        .uploader(admin.id())
        .file(OWNED_JAVA_FILE)
        .content("v2")
        .file(OWNED_TXT_FILE)
        .content("unowned")
        .create();

    Response<FilesOwnersResponse> response =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId.toString())));
    assertThat(response.value().files()).containsExactly(OWNED_JAVA_FILE, owners(admin));
    assertThat(response.value().filesApproved()).isEmpty();
    assertThat(response.value().filesAutoApproved()).isEmpty();
  }

  @Test
  public void shouldNotReturnFilesAutoApprovedWhenOnlyUnownedFileIsAdded() throws Exception {
    setupAutoApprovalForJavaMatcher(admin);

    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .owner(admin.id())
            .file(OWNED_JAVA_FILE)
            .content("v1")
            .create();
    vote(admin, changeId.toString(), 2);
    changeOperations
        .change(changeId)
        .newPatchset()
        .uploader(admin.id())
        .file(OWNED_TXT_FILE)
        .content("unowned")
        .create();

    assertFilesApproval(
        changeId.toString(), OWNED_JAVA_FILE, owners(admin), NO_AUTO_APPROVED_OWNERS);
  }

  @Test
  public void shouldReturnFilesAutoApprovedWhenNextPatchSetAddsOnlyOwnedFile() throws Exception {
    setupAutoApprovalForJavaMatcher(admin);

    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .owner(admin.id())
            .file(OWNED_JAVA_FILE)
            .content("owned")
            .file(OWNED_TXT_FILE)
            .content("unowned")
            .create();
    vote(admin, changeId.toString(), 2);
    changeOperations
        .change(changeId)
        .newPatchset()
        .uploader(admin.id())
        .file("bar.java")
        .content("owned")
        .create();

    Response<FilesOwnersResponse> response =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId.toString())));
    assertThat(response.value().files()).isEmpty();
    assertThat(response.value().filesApproved()).isEmpty();
    assertThat(response.value().filesAutoApproved())
        .containsExactly(OWNED_JAVA_FILE, owners(admin), "bar.java", owners(admin));
  }

  @Test
  @GlobalPluginConfig(pluginName = "owners", name = "owners.expandGroups", value = "false")
  public void shouldReturnResponseWithUnexpandedFileOwners() throws Exception {
    addOwnerFileToRoot(true);
    String changeId = createChange().getChangeId();

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files())
        .containsExactly("a.txt", Sets.newHashSet(new GroupOwner(admin.username())));
    assertThat(resp.value().filesApproved()).isEmpty();
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

    assertThat(resp.value().files()).isEmpty();
    assertThat(resp.value().filesApproved())
        .containsExactly("a.txt", Sets.newHashSet(new GroupOwner(admin.username())));
  }

  @Test
  @GlobalPluginConfig(pluginName = "owners", name = "owners.expandGroups", value = "false")
  public void shouldReturnResponseWithUnexpandedFileMatchersOwners() throws Exception {
    addOwnerFileWithMatchersToRoot(true);
    String changeId = createChange().getChangeId();

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files())
        .containsExactly("a.txt", Sets.newHashSet(new GroupOwner(admin.username())));
    assertThat(resp.value().filesApproved()).isEmpty();
  }

  @Test
  public void shouldNotApproveOwnedFilesWhenCustomLabelInOwnersFileNotProvided() throws Exception {
    addOwnerFileToRootWithLabel(LabelDefinition.parse("Foo,1").get(), admin);
    String changeId = createChange().getChangeId();
    approve(changeId);

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files())
        .containsExactly("a.txt", Sets.newHashSet(new Owner(admin.fullName(), admin.id().get())));
    assertThat(resp.value().filesApproved()).isEmpty();
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
    assertThat(resp.value().files()).containsExactly("a.txt", Sets.newHashSet(rootOwner));

    addOwnerFileToProjectConfig(allProjects, true, user);
    resp = assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));
    assertThat(resp.value().files()).containsExactly("a.txt", Sets.newHashSet(projectOwner));
    assertThat(resp.value().filesApproved()).isEmpty();
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

    assertThat(resp.value().files()).containsExactly("a.txt", Sets.newHashSet(rootOwner));
    assertThat(resp.value().filesApproved()).isEmpty();
  }

  private void assertInheritFromProject(Project.NameKey projectNameKey) throws Exception {
    addOwnerFileToRoot(true);
    addOwnerFileToProjectConfig(projectNameKey, true);

    String changeId = createChange().getChangeId();
    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files())
        .containsExactly("a.txt", Sets.newHashSet(rootOwner, projectOwner));
    assertThat(resp.value().filesApproved()).isEmpty();
  }

  private void addBrokenOwnersFileToRoot() throws Exception {
    merge(createChange(testRepo, "master", "Add OWNER file", "OWNERS", "{foo", ""));
  }

  private Change.Id createChangeWithCopiedOwnerVote(TestAccount owner) throws Exception {
    Change.Id changeId =
        changeOperations
            .newChange()
            .project(project)
            .owner(owner.id())
            .file(OWNED_JAVA_FILE)
            .content("v1")
            .create();
    vote(owner, changeId.toString(), 2);
    changeOperations
        .change(changeId)
        .newPatchset()
        .uploader(owner.id())
        .file(OWNED_JAVA_FILE)
        .content("v2")
        .create();
    return changeId;
  }

  private void setupAutoApprovalFor(TestAccount... owners) throws Exception {
    updateLabel(b -> b.setCopyCondition("approverin:" + FULL_OPERAND_WITH_PLUGIN_NAME));
    String ownersYaml =
        Arrays.stream(owners)
            .map(owner -> String.format("- %s\n", owner.username()))
            .collect(Collectors.joining());
    merge(
        createChange(
            testRepo,
            "master",
            "Add OWNER file",
            "OWNERS",
            String.format("inherited: true\nauto-owners-approved: true\nowners:\n%s", ownersYaml),
            ""));
  }

  private void setupAutoApprovalForJavaMatcher(TestAccount owner) throws Exception {
    updateLabel(b -> b.setCopyCondition("approverin:" + FULL_OPERAND_WITH_PLUGIN_NAME));
    merge(
        createChange(
            testRepo,
            "master",
            "Add OWNER file",
            "OWNERS",
            String.format(
                "inherited: true\nmatchers:\n"
                    + "- suffix: .java\n"
                    + "  auto-owners-approved: true\n"
                    + "  owners:\n"
                    + "   - %s\n",
                owner.username()),
            ""));
  }

  private void assertFilesApproval(
      String changeId,
      String filePath,
      java.util.Set<GroupOwner> explicitlyApprovedOwners,
      java.util.Set<GroupOwner> autoApprovedOwners)
      throws Exception {
    Response<FilesOwnersResponse> response =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));
    assertThat(response.value().files()).isEmpty();
    if (explicitlyApprovedOwners.isEmpty()) {
      assertThat(response.value().filesApproved()).isEmpty();
    } else {
      assertThat(response.value().filesApproved())
          .containsExactly(filePath, explicitlyApprovedOwners);
    }
    if (autoApprovedOwners.isEmpty()) {
      assertThat(response.value().filesAutoApproved()).isEmpty();
    } else {
      assertThat(response.value().filesAutoApproved())
          .containsExactly(filePath, autoApprovedOwners);
    }
  }

  private Set<GroupOwner> owners(TestAccount... accounts) {
    return java.util.Arrays.stream(accounts)
        .map(account -> new Owner(account.fullName(), account.id().get()))
        .collect(Collectors.toSet());
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

  private void vote(TestAccount user, String changeId, int vote) throws Exception {
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(changeId)
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, vote));
  }

  private void updateLabel(java.util.function.Consumer<LabelType.Builder> update) throws Exception {
    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      u.getConfig().updateLabelType(LabelId.CODE_REVIEW, update);
      u.save();
    }
  }

  private void allowCodeReviewForRegisteredUsers() throws Exception {
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowLabel(LabelId.CODE_REVIEW).ref("refs/*").group(REGISTERED_USERS).range(-2, 2))
        .update();
  }
}
