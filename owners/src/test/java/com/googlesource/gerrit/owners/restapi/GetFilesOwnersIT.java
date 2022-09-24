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
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GlobalPluginConfig;
import com.google.gerrit.extensions.restapi.Response;
import com.googlesource.gerrit.owners.entities.FilesOwnersResponse;
import com.googlesource.gerrit.owners.entities.GroupOwner;
import com.googlesource.gerrit.owners.entities.Owner;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.compress.utils.Sets;
import org.junit.Test;

@TestPlugin(name = "owners", httpModule = "com.googlesource.gerrit.owners.OwnersRestApiModule")
@UseLocalDisk
public class GetFilesOwnersIT extends LightweightPluginDaemonTest {

  private GetFilesOwners ownersApi;

  @Override
  public void setUpTestPlugin() throws Exception {
    super.setUpTestPlugin();

    ownersApi = plugin.getSysInjector().getInstance(GetFilesOwners.class);

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
            "owners:\n" + "- " + admin.username() + "\n",
            ""));
  }

  @Test
  public void shouldReturnExactFileOwners() throws Exception {
    String changeId = createChange().getChangeId();

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files)
        .containsExactly("a.txt", Sets.newHashSet(new Owner(admin.fullName(), admin.id().get())));
  }

  @Test
  public void shouldReturnOwnersLabels() throws Exception {
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
    String changeId = createChange().getChangeId();

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));

    assertThat(resp.value().files)
        .containsExactly("a.txt", Sets.newHashSet(new GroupOwner(admin.username())));
  }

  private static <T> Response<T> assertResponseOk(Response<T> response) {
    assertThat(response.statusCode()).isEqualTo(HttpServletResponse.SC_OK);
    return response;
  }
}
