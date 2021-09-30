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
import static com.googlesource.gerrit.owners.common.AutoassignConfigModule.PROJECT_CONFIG_AUTOASSIGN_FIELD;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.googlesource.gerrit.owners.api.OwnersApiModule;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public abstract class AbstractAutoassignIT extends LightweightPluginDaemonTest {
  private static final String PLUGIN_NAME = "owners-api";

  private final String section;
  private final boolean INHERITED = true;
  private final boolean NOT_INHERITED = false;
  private final ReviewerState assignedUserState;

  @SuppressWarnings("hiding")
  @Inject
  private ProjectConfig.Factory projectConfigFactory;

  AbstractAutoassignIT(String section, ReviewerState assignedUserState) {
    this.section = section;
    this.assignedUserState = assignedUserState;
  }

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new AutoassignModule());
    }
  }

  @Override
  public Module createModule() {
    return new OwnersApiModule();
  }

  @Override
  public void setUpTestPlugin() throws Exception {
    super.setUpTestPlugin();

    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      ProjectConfig projectConfig = projectConfigFactory.create(project);
      projectConfig.load(md);
      projectConfig.updatePluginConfig(
          PLUGIN_NAME,
          cfg -> cfg.setString(PROJECT_CONFIG_AUTOASSIGN_FIELD, assignedUserState.name()));
      projectConfig.commit(md);
      projectCache.evict(project);
    }
  }

  @Test
  public void shouldAutoassignOneOwner() throws Exception {
    String ownerEmail = user.email();

    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "Set OWNERS",
            "OWNERS",
            "inherited: false\n" + "owners:\n" + "- " + ownerEmail)
        .to("refs/heads/master")
        .assertOkStatus();

    ChangeApi changeApi = change(createChange());
    Collection<AccountInfo> reviewers = changeApi.get().reviewers.get(ReviewerState.REVIEWER);
  }

  @Test
  public void shouldAutoassignUserInPath() throws Exception {
    String ownerEmail = user.email();

    addOwnersToRepo("", ownerEmail, NOT_INHERITED);

    Collection<AccountInfo> reviewers = getAutoassignedAccounts(change(createChange()));

    assertThat(reviewers).isNotNull();
    assertThat(reviewers).hasSize(1);
    assertThat(reviewersEmail(reviewers).get(0)).isEqualTo(ownerEmail);
  }

  @Test
  public void shouldAutoassignUserInPathWithInheritance() throws Exception {
    String childOwnersEmail = accountCreator.user2().email();
    String parentOwnersEmail = user.email();
    String childpath = "childpath/";

    addOwnersToRepo("", parentOwnersEmail, NOT_INHERITED);
    addOwnersToRepo(childpath, childOwnersEmail, INHERITED);

    Collection<AccountInfo> reviewers =
        getAutoassignedAccounts(change(createChange("test change", childpath + "foo.txt", "foo")));

    assertThat(reviewers).isNotNull();
    assertThat(reviewersEmail(reviewers)).containsExactly(parentOwnersEmail, childOwnersEmail);
  }

  @Test
  public void shouldAutoassignUserInPathWithoutInheritance() throws Exception {
    String childOwnersEmail = accountCreator.user2().email();
    String parentOwnersEmail = user.email();
    String childpath = "childpath/";

    addOwnersToRepo("", parentOwnersEmail, NOT_INHERITED);
    addOwnersToRepo(childpath, childOwnersEmail, NOT_INHERITED);

    Collection<AccountInfo> reviewers =
        getAutoassignedAccounts(change(createChange("test change", childpath + "foo.txt", "foo")));

    assertThat(reviewers).isNotNull();
    assertThat(reviewersEmail(reviewers)).containsExactly(childOwnersEmail);
  }

  @Test
  public void shouldAutoassignUserMatchingPath() throws Exception {
    String ownerEmail = user.email();

    addOwnersToRepo("", "suffix", ".java", ownerEmail, NOT_INHERITED);

    Collection<AccountInfo> reviewers =
        getAutoassignedAccounts(change(createChange("test change", "foo.java", "foo")));

    assertThat(reviewers).isNotNull();
    assertThat(reviewersEmail(reviewers)).containsExactly(ownerEmail);
  }

  @Test
  public void shouldNotAutoassignUserNotMatchingPath() throws Exception {
    String ownerEmail = user.email();

    addOwnersToRepo("", "suffix", ".java", ownerEmail, NOT_INHERITED);

    ChangeApi changeApi = change(createChange("test change", "foo.bar", "foo"));
    Collection<AccountInfo> reviewers = getAutoassignedAccounts(changeApi);

    assertThat(reviewers).isNull();
  }

  @Test
  public void shouldAutoassignUserMatchingPathWithInheritance() throws Exception {
    String childOwnersEmail = accountCreator.user2().email();
    String parentOwnersEmail = user.email();
    String childpath = "childpath/";

    addOwnersToRepo("", "suffix", ".java", parentOwnersEmail, NOT_INHERITED);
    addOwnersToRepo(childpath, "suffix", ".java", childOwnersEmail, INHERITED);

    ChangeApi changeApi = change(createChange("test change", childpath + "foo.java", "foo"));
    Collection<AccountInfo> reviewers = getAutoassignedAccounts(changeApi);

    assertThat(reviewers).isNotNull();
    assertThat(reviewersEmail(reviewers)).containsExactly(parentOwnersEmail, childOwnersEmail);
  }

  @Test
  public void shouldAutoassignUserMatchingPathWithoutInheritance() throws Exception {
    String childOwnersEmail = accountCreator.user2().email();
    String parentOwnersEmail = user.email();
    String childpath = "childpath/";

    addOwnersToRepo("", parentOwnersEmail, NOT_INHERITED);
    addOwnersToRepo(childpath, "suffix", ".java", childOwnersEmail, NOT_INHERITED);

    ChangeApi changeApi = change(createChange("test change", childpath + "foo.java", "foo"));
    Collection<AccountInfo> reviewers = getAutoassignedAccounts(changeApi);

    assertThat(reviewers).isNotNull();
    assertThat(reviewersEmail(reviewers)).containsExactly(childOwnersEmail);
  }

  private Collection<AccountInfo> getAutoassignedAccounts(ChangeApi changeApi)
      throws RestApiException {
    Collection<AccountInfo> reviewers = changeApi.get().reviewers.get(assignedUserState);
    return reviewers;
  }

  private List<String> reviewersEmail(Collection<AccountInfo> reviewers) {
    List<String> reviewersEmail = reviewers.stream().map(a -> a.email).collect(Collectors.toList());
    return reviewersEmail;
  }

  private void addOwnersToRepo(String parentPath, String ownerEmail, boolean inherited)
      throws Exception {
    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "Set OWNERS",
            parentPath + "OWNERS",
            "inherited: " + inherited + "\n" + section + ":\n" + "- " + ownerEmail)
        .to("refs/heads/master")
        .assertOkStatus();
  }

  private void addOwnersToRepo(
      String parentPath,
      String matchingType,
      String patternMatch,
      String ownerEmail,
      boolean inherited)
      throws Exception {
    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "Set OWNERS",
            parentPath + "OWNERS",
            "inherited: "
                + inherited
                + "\n"
                + "matchers:\n"
                + "- "
                + matchingType
                + ": "
                + patternMatch
                + "\n"
                + "  "
                + section
                + ":\n"
                + "  - "
                + ownerEmail)
        .to("refs/heads/master")
        .assertOkStatus();
  }
}
