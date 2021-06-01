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

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.inject.AbstractModule;
import java.util.Collection;
import org.junit.Test;

@TestPlugin(
    name = "owners-api",
    sysModule = "com.googlesource.gerrit.owners.common.OwnersAutoassignIT$TestModule")
public class OwnersAutoassignIT extends LightweightPluginDaemonTest {

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new AutoassignModule());
    }
  }

  @Test
  public void shouldAutoassignOneOwner() throws Exception {
    shouldAutoassignUser("owners", user.email());
  }

  @Test
  public void shouldAutoassignOneReviewer() throws Exception {
    shouldAutoassignUser("reviewers", user.email());
  }

  private void shouldAutoassignUser(String section, String autoAssignUser) throws Exception {
    String ownerEmail = user.email();

    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "Set OWNERS",
            "OWNERS",
            "inherited: false\n" + section + ":\n" + "- " + autoAssignUser)
        .to("refs/heads/master")
        .assertOkStatus();

    ChangeApi changeApi = change(createChange());
    Collection<AccountInfo> reviewers = changeApi.get().reviewers.get(ReviewerState.REVIEWER);

    assertThat(reviewers).isNotNull();
    assertThat(reviewers).hasSize(1);
    AccountInfo reviewer = reviewers.iterator().next();
    assertThat(reviewer.email).isEqualTo(ownerEmail);
  }
}
