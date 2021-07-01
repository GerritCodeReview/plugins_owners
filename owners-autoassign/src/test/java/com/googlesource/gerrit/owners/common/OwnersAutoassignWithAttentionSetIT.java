// Copyright (C) 2021 The Android Open Source Project
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

package com.googlesource.gerrit.owners.common;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.Account.Id;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.googlesource.gerrit.owners.api.OwnersApiModule;
import com.googlesource.gerrit.owners.api.OwnersAttentionSet;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;

@TestPlugin(
    name = "owners-autoassign",
    sysModule =
        "com.googlesource.gerrit.owners.common.OwnersAutoassignWithAttentionSetIT$TestModule")
public class OwnersAutoassignWithAttentionSetIT extends LightweightPluginDaemonTest {

  @Inject private ProjectOperations projectOperations;

  @Override
  public Module createModule() {
    return new OwnersApiModule();
  }

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new AutoassignModule());

      DynamicItem.bind(binder(), OwnersAttentionSet.class)
          .to(SelectFirstOwnerForAttentionSet.class)
          .in(Scopes.SINGLETON);
    }
  }

  public static class SelectFirstOwnerForAttentionSet implements OwnersAttentionSet {

    @Override
    public Collection<Id> addToAttentionSet(ChangeInfo changeInfo, Collection<Id> owners) {
      return owners.stream().limit(1).collect(toList());
    }
  }

  @Test
  public void shouldAutoassignTwoOwnersWithOneAttentionSet() throws Exception {
    String ownerEmail1 = user.email();
    String ownerEmail2 = accountCreator.user2().email();

    setOwners(ownerEmail1, ownerEmail2);

    ChangeInfo change = change(createChange()).get();
    assertThat(change.reviewers.get(ReviewerState.REVIEWER)).hasSize(2);
    assertThat(change.attentionSet).hasSize(1);
  }

  @Test
  public void shouldAutoassignUserIfAnotherUserHasNoPermission() throws Exception {
    TestAccount userWithAccessToProject = accountCreator.user();
    TestAccount userWithNoAccessToProject = accountCreator.user2();

    AccountGroup.UUID groupWithNoAccessToProject =
        createGroup("groupWithNoAccessToProject", userWithNoAccessToProject);

    setOwners(userWithAccessToProject.email(), userWithNoAccessToProject.email());

    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(AccessSection.ALL).group(groupWithNoAccessToProject))
        .update();

    ChangeInfo change = change(createChange()).get();
    assertThat(change.attentionSet).isNotNull();
    assertThat(change.attentionSet.keySet()).containsExactly(userWithAccessToProject.id().get());
  }

  private void setOwners(String ownerEmail1, String ownerEmail2) throws Exception {
    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "Set OWNERS",
            "OWNERS",
            "inherited: false\n"
                + "owners:\n"
                + "- "
                + ownerEmail1
                + "\n"
                + "- "
                + ownerEmail2
                + "\n")
        .to("refs/heads/master")
        .assertOkStatus();
  }

  private AccountGroup.UUID createGroup(String name, TestAccount member) throws RestApiException {
    GroupInput groupInput = new GroupInput();
    groupInput.name = name(name);
    groupInput.members = Collections.singletonList(String.valueOf(member.id().get()));
    return AccountGroup.uuid(gApi.groups().create(groupInput).get().id);
  }
}
