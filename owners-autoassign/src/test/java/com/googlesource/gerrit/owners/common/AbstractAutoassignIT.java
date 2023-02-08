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
import static com.googlesource.gerrit.owners.common.AutoAssignConfigModule.PROJECT_CONFIG_AUTOASSIGN_FIELD;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GlobalPluginConfig;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ChangeEditApi;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private TestAccount user2;

  private TestAccount admin2;

  AbstractAutoassignIT(String section, ReviewerState assignedUserState) {
    this.section = section;
    this.assignedUserState = assignedUserState;
  }

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new AutoAssignModule(new AutoAssignConfig()));
    }
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

    user2 = accountCreator.user2();
    admin2 = accountCreator.admin2();
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

    Collection<AccountInfo> reviewers = getAutoassignedAccounts(change(createChange()).get());

    assertThat(reviewers).isNotNull();
    assertThat(reviewers).hasSize(1);
    assertThat(reviewersEmail(reviewers).get(0)).isEqualTo(ownerEmail);
  }

  @Test
  @UseLocalDisk // Required when using @GlobalPluginConfig
  @GlobalPluginConfig(
      pluginName = "owners-api",
      name = "owners.disable.branch",
      value = "refs/heads/master")
  public void shouldNotAutoassignUserInPathWhenBranchIsDisabled() throws Exception {
    addOwnersToRepo("", user.email(), NOT_INHERITED);

    assertThat(getAutoassignedAccounts(change(createChange()).get())).isNull();
  }

  @Test
  public void shouldNotReAutoassignUserInPath() throws Exception {
    String ownerEmail = user.email();

    addOwnersToRepo("", ownerEmail, NOT_INHERITED);

    ChangeApi changeApi = change(createChange());
    ChangeInfo changeInfo = changeApi.get();
    Collection<AccountInfo> reviewers = getAutoassignedAccounts(changeInfo);
    assertThat(reviewers).hasSize(1);

    // Switch user from CC to Reviewer or the other way around
    AddReviewerInput switchReviewerInput = new AddReviewerInput();
    switchReviewerInput.reviewer = ownerEmail;
    switchReviewerInput.state =
        assignedUserState == ReviewerState.REVIEWER ? ReviewerState.CC : ReviewerState.REVIEWER;
    changeApi.addReviewer(switchReviewerInput);

    ChangeEditApi changeEdit = changeApi.edit();
    changeEdit.create();
    changeEdit.modifyFile("foo", RawInputUtil.create("foo content"));
    changeEdit.publish();

    // It should not re-assign any user
    assertThat(getAutoassignedAccounts(changeInfo)).isNull();
  }

  @Test
  public void shouldAutoassignUserInPathWithInheritance() throws Exception {
    String childOwnersEmail = accountCreator.user2().email();
    String parentOwnersEmail = user.email();
    String childpath = "childpath/";

    addOwnersToRepo("", parentOwnersEmail, NOT_INHERITED);
    addOwnersToRepo(childpath, childOwnersEmail, INHERITED);

    Collection<AccountInfo> reviewers =
        getAutoassignedAccounts(
            change(createChange("test change", childpath + "foo.txt", "foo")).get());

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
        getAutoassignedAccounts(
            change(createChange("test change", childpath + "foo.txt", "foo")).get());

    assertThat(reviewers).isNotNull();
    assertThat(reviewersEmail(reviewers)).containsExactly(childOwnersEmail);
  }

  @Test
  public void shouldAutoassignUserMatchingPath() throws Exception {
    String ownerEmail = user.email();

    addOwnersToRepo("", NOT_INHERITED, "suffix", ".java", ownerEmail);

    Collection<AccountInfo> reviewers =
        getAutoassignedAccounts(change(createChange("test change", "foo.java", "foo")).get());

    assertThat(reviewers).isNotNull();
    assertThat(reviewersEmail(reviewers)).containsExactly(ownerEmail);
  }

  @Test
  public void shouldNotAutoassignUserNotMatchingPath() throws Exception {
    String ownerEmail = user.email();

    addOwnersToRepo("", NOT_INHERITED, "suffix", ".java", ownerEmail);

    ChangeApi changeApi = change(createChange("test change", "foo.bar", "foo"));
    Collection<AccountInfo> reviewers = getAutoassignedAccounts(changeApi.get());

    assertThat(reviewers).isNull();
  }

  @Test
  public void shouldAutoassignUserWithGenericTopLevelFallback() throws Exception {
    String ownerEmail = user.email();
    String owner2Email = user2.email();
    String admin2Email = admin2.email();

    addOwnersToRepo(
        "",
        NOT_INHERITED,
        "suffix",
        ".java",
        ownerEmail,
        "generic",
        ".*\\.c",
        admin2Email,
        "generic",
        ".*",
        owner2Email);

    ChangeApi changeApi =
        change(createChangeWithFiles("test change", "foo.bar", "foo", "foo.java", "Java code"));
    Collection<AccountInfo> reviewers = getAutoassignedAccounts(changeApi.get());

    assertThat(reviewers).isNotNull();
    assertThat(reviewersEmail(reviewers)).containsExactly(ownerEmail, owner2Email);
  }

  @Test
  public void shouldAutoassignUserWithGenericMidLevelFallback() throws Exception {
    String ownerEmail = user.email();
    String owner2Email = user2.email();
    String admin2Email = admin2.email();

    addOwnersToRepo(
        "",
        NOT_INHERITED,
        "suffix",
        ".java",
        ownerEmail,
        "generic",
        ".*\\.c",
        admin2Email,
        "generic",
        ".*",
        owner2Email);

    ChangeApi changeApi =
        change(createChangeWithFiles("test change", "foo.c", "foo", "foo.java", "Java code"));
    Collection<AccountInfo> reviewers = getAutoassignedAccounts(changeApi.get());

    assertThat(reviewers).isNotNull();
    assertThat(reviewersEmail(reviewers)).containsExactly(ownerEmail, admin2Email);
  }

  @Test
  public void shouldNotAutoassignUserWithNonMatchingGenericFallback() throws Exception {
    String ownerEmail = user.email();
    String owner2Email = user2.email();

    addOwnersToRepo(
        "", NOT_INHERITED, "suffix", ".java", ownerEmail, "generic", "\\.c", owner2Email);

    ChangeApi changeApi =
        change(createChangeWithFiles("test change", "foo.bar", "foo", "foo.groovy", "Groovy code"));
    Collection<AccountInfo> reviewers = getAutoassignedAccounts(changeApi.get());

    assertThat(reviewers).isNull();
  }

  @Test
  public void shouldAutoassignUserWithMultipleGenericFallback() throws Exception {
    String admin2Email = admin2.email();
    String ownerEmail = user.email();
    String owner2Email = user2.email();

    addOwnersToRepo(
        "",
        NOT_INHERITED,
        "suffix",
        ".java",
        admin2Email,
        "generic",
        ".*\\.c",
        ownerEmail,
        "generic",
        ".*",
        owner2Email);

    ChangeApi changeApi =
        change(createChangeWithFiles("test change", "foo.bar", "foo", "foo.c", "C code"));
    Collection<AccountInfo> reviewers = getAutoassignedAccounts(changeApi.get());

    assertThat(reviewers).isNotNull();
    assertThat(reviewersEmail(reviewers)).containsExactly(ownerEmail, owner2Email);
  }

  @Test
  public void shouldAutoassignUserMatchingPathWithInheritance() throws Exception {
    String childOwnersEmail = accountCreator.user2().email();
    String parentOwnersEmail = user.email();
    String childpath = "childpath/";

    addOwnersToRepo("", NOT_INHERITED, "suffix", ".java", parentOwnersEmail);
    addOwnersToRepo(childpath, INHERITED, "suffix", ".java", childOwnersEmail);

    ChangeApi changeApi = change(createChange("test change", childpath + "foo.java", "foo"));
    Collection<AccountInfo> reviewers = getAutoassignedAccounts(changeApi.get());

    assertThat(reviewers).isNotNull();
    assertThat(reviewersEmail(reviewers)).containsExactly(parentOwnersEmail, childOwnersEmail);
  }

  @Test
  public void shouldAutoassignUserMatchingPathWithoutInheritance() throws Exception {
    String childOwnersEmail = accountCreator.user2().email();
    String parentOwnersEmail = user.email();
    String childpath = "childpath/";

    addOwnersToRepo("", parentOwnersEmail, NOT_INHERITED);
    addOwnersToRepo(childpath, NOT_INHERITED, "suffix", ".java", childOwnersEmail);

    ChangeApi changeApi = change(createChange("test change", childpath + "foo.java", "foo"));
    Collection<AccountInfo> reviewers = getAutoassignedAccounts(changeApi.get());

    assertThat(reviewers).isNotNull();
    assertThat(reviewersEmail(reviewers)).containsExactly(childOwnersEmail);
  }

  protected PushOneCommit.Result createChangeWithFiles(String subject, String... filesWithContent)
      throws Exception {
    Map<String, String> files = new HashMap<>();
    for (int i = 0; i < filesWithContent.length; ) {
      String fileName = filesWithContent[i++];
      String fileContent = filesWithContent[i++];
      files.put(fileName, fileContent);
    }
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, subject, files);
    return push.to("refs/for/master");
  }

  private Collection<AccountInfo> getAutoassignedAccounts(ChangeInfo changeInfo)
      throws RestApiException {
    Collection<AccountInfo> reviewers =
        gApi.changes().id(changeInfo._number).get().reviewers.get(assignedUserState);
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

  private void addOwnersToRepo(String parentPath, boolean inherited, String... matchingRules)
      throws Exception {
    StringBuilder ownersStringBuilder =
        new StringBuilder("inherited: " + inherited + "\n" + "matchers:\n");
    for (int i = 0; i < matchingRules.length; ) {
      String matchingType = matchingRules[i++];
      String patternMatch = matchingRules[i++];
      String ownerEmail = matchingRules[i++];

      ownersStringBuilder
          .append("- ")
          .append(matchingType)
          .append(": ")
          .append(patternMatch)
          .append("\n")
          .append("  ")
          .append(section)
          .append(":\n")
          .append("  - ")
          .append(ownerEmail)
          .append("\n");
    }
    pushFactory
        .create(
            admin.newIdent(),
            testRepo,
            "Set OWNERS",
            parentPath + "OWNERS",
            ownersStringBuilder.toString())
        .to("refs/heads/master")
        .assertOkStatus();
  }
}
