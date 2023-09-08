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

import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.inject.Inject;
import com.googlesource.gerrit.owners.common.LabelDefinition;
import com.googlesource.gerrit.owners.entities.FilesOwnersResponse;
import com.googlesource.gerrit.owners.entities.Owner;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.apache.commons.compress.utils.Sets;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

@TestPlugin(name = "owners", sysModule = "com.googlesource.gerrit.owners.OwnersModule")
@UseLocalDisk
public class GetFilesOwnersSubmitRequirementsIT extends GetFilesOwnersITAbstract {
  @Inject private ProjectOperations projectOperations;

  @Override
  public void setUpTestPlugin() throws Exception {
    Config pluginCfg = pluginConfig.getGlobalPluginConfig("owners");
    // enable submit requirements and store them to the file as there is no `ConfigSuite` mechanism
    // for plugin config and there is no other way (but adding it to each test case to enable it
    // globally
    pluginCfg.setBoolean("owners", null, "enableSubmitRequirement", true);
    Files.writeString(
        server.getSitePath().resolve("etc").resolve("owners.config"),
        pluginCfg.toText(),
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
    super.setUpTestPlugin();
  }

  @Test
  public void shouldRequireConfiguredCodeReviewScore() throws Exception {
    // configure submit requirement to require CR+1 only
    addOwnerFileToRoot(true, LabelDefinition.parse("Code-Review,1").get(), admin);

    String changeId = createChange("Add a file", "foo", "bar").getChangeId();

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));
    assertThat(resp.value().files)
        .containsExactly("foo", Sets.newHashSet(new Owner(admin.fullName(), admin.id().get())));
    assertThat(resp.value().ownersLabels).isEmpty();

    // give CR+1 as requested
    recommend(changeId);

    resp = assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));
    assertThat(resp.value().files).isEmpty();
    assertThat(resp.value().ownersLabels)
        .containsExactly(admin.id().get(), Map.of(LabelId.CODE_REVIEW, 1));
  }

  @Test
  public void shouldRequireConfiguredLabelAndScore() throws Exception {
    // configure submit requirement to require LabelFoo+1
    String label = "LabelFoo";
    addOwnerFileToRoot(true, LabelDefinition.parse(String.format("%s,1", label)).get(), admin);
    replaceCodeReviewWithLabel(label);

    String changeId = createChange("Add a file", "foo", "bar").getChangeId();

    Response<FilesOwnersResponse> resp =
        assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));
    assertThat(resp.value().files)
        .containsExactly("foo", Sets.newHashSet(new Owner(admin.fullName(), admin.id().get())));
    assertThat(resp.value().ownersLabels).isEmpty();

    // give LabelFoo+1 as requested
    gApi.changes().id(changeId).current().review(new ReviewInput().label(label, 1));

    resp = assertResponseOk(ownersApi.apply(parseCurrentRevisionResource(changeId)));
    assertThat(resp.value().files).isEmpty();
    assertThat(resp.value().ownersLabels).containsEntry(admin.id().get(), Map.of(label, 1));
  }

  private void addOwnerFileToRoot(boolean inherit, LabelDefinition label, TestAccount u)
      throws Exception {
    // Add OWNERS file to root:
    //
    // inherited: inherit
    // label: label,score # score is optional
    // owners:
    // - u.email()
    String owners =
        String.format(
            "inherited: %s\nlabel: %s\nowners:\n- %s\n",
            inherit,
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

  private void replaceCodeReviewWithLabel(String labelId) throws Exception {
    LabelType label =
        TestLabels.label(labelId, TestLabels.value(1, "OK"), TestLabels.value(-1, "Not OK"));

    replaceCodeReviewWithLabel(label);

    // grant label to RegisteredUsers so that it is vote-able
    String heads = RefNames.REFS_HEADS + "*";
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel(label.getName()).ref(heads).group(REGISTERED_USERS).range(-1, 1))
        .update();
  }
}
