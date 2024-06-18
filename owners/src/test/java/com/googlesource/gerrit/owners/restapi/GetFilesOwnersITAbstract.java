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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GlobalPluginConfig;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.project.testing.TestLabels;
import com.googlesource.gerrit.owners.common.InvalidOwnersFileException;
import com.googlesource.gerrit.owners.entities.FilesOwnersResponse;
import com.googlesource.gerrit.owners.entities.GroupOwner;
import com.googlesource.gerrit.owners.entities.Owner;
import com.googlesource.gerrit.owners.restapi.GetFilesOwners.LabelNotFoundException;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.compress.utils.Sets;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

public abstract class GetFilesOwnersITAbstract
    extends LightweightPluginDaemonTest {

  private static final String REFS_META_CONFIG = RefNames.REFS_META + "config";
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
  public void shouldReturnOwnersLabelsWhenNotApprovedByOwners() throws Exception {
    addOwnerFileToRoot(true);
    String changeId = createChange().getChangeId();

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files)
        .containsExactly("a.txt", Sets.newHashSet(new Owner(admin.fullName(), admin.id().get())));

    assertThat(resp.value().ownersLabels).isEmpty();
  }

  @Test
  public void shouldReturnEmptyResponseWhenApprovedByOwners() throws Exception {
    addOwnerFileToRoot(true);
    String changeId = createChange().getChangeId();
    approve(changeId);

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files).isEmpty();
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
  }

  @Test
  @GlobalPluginConfig(pluginName = "owners", name = "owners.expandGroups", value = "false")
  public void shouldReturnEmptyResponseWhenApprovedByOwnersWithUnexpandedFileOwners()
      throws Exception {
    addOwnerFileToRoot(true);
    String changeId = createChange().getChangeId();
    approve(changeId);

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files).isEmpty();
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

    assertThat(resp.value().files).containsExactly("a.txt", Sets.newHashSet(rootOwner));
  }

  private void assertInheritFromProject(Project.NameKey projectNameKey) throws Exception {
    addOwnerFileToRoot(true);
    addOwnerFileToProjectConfig(projectNameKey, true);

    String changeId = createChange().getChangeId();
    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files)
        .containsExactly("a.txt", Sets.newHashSet(rootOwner, projectOwner));
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
    return cloneProject(project);
  }
}
