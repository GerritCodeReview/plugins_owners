// Copyright (C) 2022 The Android Open Source Project
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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GlobalPluginConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.Response;
import com.googlesource.gerrit.owners.entities.FilesOwnersResponse;
import com.googlesource.gerrit.owners.entities.GroupOwner;
import com.googlesource.gerrit.owners.entities.Owner;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.compress.utils.Sets;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

@TestPlugin(name = "owners", sysModule = "com.googlesource.gerrit.owners.OwnersModule")
@UseLocalDisk
public class GetFilesOwnersIT extends LightweightPluginDaemonTest {

  private GetFilesOwners ownersApi;
  private Owner rootOwner;
  private Owner projectOwner;

  @Override
  public void setUpTestPlugin() throws Exception {
    super.setUpTestPlugin();

    rootOwner = new Owner(admin.fullName(), admin.id().get());
    projectOwner = new Owner(user.fullName(), user.id().get());
    ownersApi = plugin.getSysInjector().getInstance(GetFilesOwners.class);
  }

  @Test
  public void shouldReturnExactFileOwners() throws Exception {
    addOwnerFileToRoot(true);
    String changeId = createChange().getChangeId();

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files).containsExactly("a.txt", Sets.newHashSet(rootOwner));
  }

  @Test
  public void shouldReturnOwnersLabels() throws Exception {
    addOwnerFileToRoot(true);
    String changeId = createChange().getChangeId();
    approve(changeId);

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().ownersLabels)
        .containsExactly(admin.id().get(), ImmutableMap.builder().put("Code-Review", 2).build());
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
  public void shouldReflectChangesInInParentProject() throws Exception {
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

  private static <T> Response<T> assertResponseOk(Response<T> response) {
    assertThat(response.statusCode()).isEqualTo(HttpServletResponse.SC_OK);
    return response;
  }

  private void assertNotInheritFromProject(Project.NameKey projectNameKey) throws Exception {
    addOwnerFileToRoot(false);
    addOwnerFileToProjectConfig(projectNameKey, true);

    String changeId = createChange().getChangeId();
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
    // Add OWNERS file to root:
    //
    // inherited: true
    // matchers:
    // - suffix: .txt
    //   owners:
    //   - admin@mail.com
    merge(
        createChange(
            testRepo,
            "master",
            "Add OWNER file",
            "OWNERS",
            String.format(
                "inherited: %s\nmatchers:\n" + "- suffix: .txt\n  owners:\n   - %s\n",
                inherit, admin.email()),
            ""));
  }
}
