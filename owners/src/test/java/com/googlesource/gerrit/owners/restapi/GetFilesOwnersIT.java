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

import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.change.RevisionResource;
import com.googlesource.gerrit.owners.entities.FilesOwnersResponse;
import com.googlesource.gerrit.owners.entities.Owner;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.compress.utils.Sets;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

@TestPlugin(name = "owners", httpModule = "com.googlesource.gerrit.owners.OwnersRestApiModule")
public class GetFilesOwnersIT extends LightweightPluginDaemonTest {

  private GetFilesOwners ownersApi;

  @Override
  public void setUpTestPlugin() throws Exception {
    super.setUpTestPlugin();

    ownersApi = plugin.getSysInjector().getInstance(GetFilesOwners.class);
  }

  @Test
  @UseLocalDisk
  public void shouldReturnACorrectResponse() throws Exception {
    // Add OWNERS file to root:
    //
    // inherited: true
    // owners:
    // - Administrator
    merge(createChange(testRepo, "master", "Add OWNER file", "OWNERS", getOwnerFileContent(), ""));

    PushOneCommit.Result result = createChange();

    approve(result.getChangeId());

    RevisionResource revisionResource = parseCurrentRevisionResource(result.getChangeId());

    Response<?> resp = ownersApi.apply(revisionResource);

    assertThat(resp.statusCode()).isEqualTo(HttpServletResponse.SC_OK);

    FilesOwnersResponse responseValue = (FilesOwnersResponse) resp.value();

    FilesOwnersResponse expectedFilesOwnerResponse =
        new FilesOwnersResponse(
            new HashMap<Integer, Map<String, Integer>>() {
              {
                put(
                    admin.id().get(),
                    new HashMap<String, Integer>() {
                      {
                        put("Code-Review", 2);
                      }
                    });
              }
            },
            new HashMap<String, Set<Owner>>() {
              {
                put("a.txt", Sets.newHashSet(new Owner(admin.fullName(), admin.id().get())));
              }
            });

    assertThat(responseValue).isEqualTo(expectedFilesOwnerResponse);
  }

  @Test
  @UseLocalDisk
  public void shouldInheritFromProjectsOwners() throws Exception {
    assertInheritFromProject(project);
  }

  @Test
  @UseLocalDisk
  public void shouldInheritFromAllProjectsOwners() throws Exception {
    assertInheritFromProject(allProjects);
  }

  @Test
  @UseLocalDisk
  public void shouldNotInheritFromProjectsSpecificOwners() throws Exception {
    assertNotInheritFromProject(project);
  }

  @Test
  @UseLocalDisk
  public void shouldNotInheritFromAllProjectsSpecificOwners() throws Exception {
    assertNotInheritFromProject(allProjects);
  }

  private void assertNotInheritFromProject(Project.NameKey projectNameKey) throws Exception {
    addOwnerFileToProjectConfig(projectNameKey);
    merge(
        createChange(
            testRepo, "master", "Add OWNER file", "OWNERS", getOwnerFileContent(false, user), ""));

    PushOneCommit.Result result = createChange();
    RevisionResource revisionResource = parseCurrentRevisionResource(result.getChangeId());

    Response<?> resp = ownersApi.apply(revisionResource);

    assertThat(resp.statusCode()).isEqualTo(HttpServletResponse.SC_OK);
    FilesOwnersResponse responseValue = (FilesOwnersResponse) resp.value();
    FilesOwnersResponse expectedFilesOwnerResponse =
        new FilesOwnersResponse(
            new HashMap<Integer, Map<String, Integer>>(),
            new HashMap<String, Set<Owner>>() {
              {
                put("a.txt", Sets.newHashSet(new Owner(user.fullName(), user.id().get())));
              }
            });

    assertThat(responseValue).isEqualTo(expectedFilesOwnerResponse);
  }

  private void assertInheritFromProject(Project.NameKey projectNameKey) throws Exception {
    addOwnerFileToProjectConfig(projectNameKey);
    PushOneCommit.Result result = createChange();
    RevisionResource revisionResource = parseCurrentRevisionResource(result.getChangeId());

    Response<?> resp = ownersApi.apply(revisionResource);

    assertThat(resp.statusCode()).isEqualTo(HttpServletResponse.SC_OK);
    FilesOwnersResponse responseValue = (FilesOwnersResponse) resp.value();
    FilesOwnersResponse expectedFilesOwnerResponse =
        new FilesOwnersResponse(
            new HashMap<Integer, Map<String, Integer>>(),
            new HashMap<String, Set<Owner>>() {
              {
                put("a.txt", Sets.newHashSet(new Owner(admin.fullName(), admin.id().get())));
              }
            });

    assertThat(responseValue).isEqualTo(expectedFilesOwnerResponse);
  }

  private String getOwnerFileContent(boolean inherit, TestAccount account) {
    return String.format("inherited: %s\nowners:\n- %s\n", inherit, account.email());
  }

  private String getOwnerFileContent() {
    return getOwnerFileContent(true, admin);
  }

  private void addOwnerFileToProjectConfig(Project.NameKey projectNameKey) throws Exception {
    TestRepository<InMemoryRepository> project = cloneProject(projectNameKey);
    GitUtil.fetch(project, RefNames.REFS_CONFIG + ":" + RefNames.REFS_CONFIG);
    project.reset(RefNames.REFS_CONFIG);
    pushFactory
        .create(admin.newIdent(), project, "Add OWNER file", "OWNERS", getOwnerFileContent())
        .to(RefNames.REFS_CONFIG);
  }
}
