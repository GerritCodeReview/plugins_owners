// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners.common;

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.owners.common.MatcherConfig.suffixMatcher;
import static java.util.Collections.emptyList;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.truth.Truth8;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class PathOwnersTest extends ClassicConfig {

  private static final String CLASSIC_OWNERS = "classic/OWNERS";
  private static final boolean EXPAND_GROUPS = true;
  private static final boolean DO_NOT_EXPAND_GROUPS = false;
  private static final String EXPECTED_LABEL = "expected-label";
  private static final Short EXPECTED_LABEL_SCORE = 1;
  private static final LabelDefinition EXPECTED_LABEL_DEFINITION =
      new LabelDefinition(EXPECTED_LABEL, EXPECTED_LABEL_SCORE);
  private static final String A_LABEL = "a-label";
  private static final String B_LABEL = "b-label";
  private static PathOwnersEntriesCache CACHE_MOCK = new PathOwnersEntriesCacheMock();

  public static final String CLASSIC_FILE_TXT = "classic/file.txt";
  public static final Project.NameKey parentRepository1NameKey =
      Project.NameKey.parse("parentRepository1");
  public static final Project.NameKey parentRepository2NameKey =
      Project.NameKey.parse("parentRepository2");

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();
  }

  @Test
  public void testClassic() throws Exception {
    mockOwners(USER_A_EMAIL_COM, USER_B_EMAIL_COM);

    PathOwners owners =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            emptyList(),
            branch,
            Set.of(CLASSIC_FILE_TXT),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.empty(),
            jgitWrapper);
    Set<Account.Id> ownersSet = owners.get().get(CLASSIC_OWNERS);
    assertEquals(2, ownersSet.size());
    assertTrue(ownersSet.contains(USER_A_ID));
    assertTrue(ownersSet.contains(USER_B_ID));
    assertTrue(owners.expandGroups());
    Truth8.assertThat(owners.getLabel()).isEmpty();
  }

  @Test
  public void testGlobalLabel() throws Exception {
    mockOwners(USER_A_EMAIL_COM, USER_B_EMAIL_COM);

    PathOwners owners =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            emptyList(),
            branch,
            Set.of(CLASSIC_FILE_TXT),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.of(EXPECTED_LABEL_DEFINITION),
            jgitWrapper);
    Truth8.assertThat(owners.getLabel()).hasValue(EXPECTED_LABEL_DEFINITION);
  }

  @Test
  public void testFileBasedOwnersUnexpanded() throws Exception {
    mockOwners(USER_A_EMAIL_COM, USER_B_EMAIL_COM);

    PathOwners owners =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            emptyList(),
            branch,
            Set.of(CLASSIC_FILE_TXT),
            DO_NOT_EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.empty(),
            jgitWrapper);
    Set<String> ownersSet = owners.getFileGroupOwners().get(CLASSIC_FILE_TXT);
    assertEquals(2, ownersSet.size());
    assertTrue(ownersSet.contains(USER_A));
    assertTrue(ownersSet.contains(USER_B));
    assertFalse(owners.expandGroups());
  }

  @Test
  public void testDisabledBranch() throws Exception {
    mockOwners(USER_A_EMAIL_COM);

    PathOwners owners =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            emptyList(),
            Optional.empty(),
            Set.of(CLASSIC_FILE_TXT),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.empty(),
            jgitWrapper);
    Set<Account.Id> ownersSet = owners.get().get(CLASSIC_OWNERS);
    assertEquals(0, ownersSet.size());
  }

  @Test
  public void testClassicWithInheritance() throws Exception {
    expectConfig("OWNERS", createConfig(true, Optional.of(A_LABEL), owners(USER_C_EMAIL_COM)));
    expectConfig(
        CLASSIC_OWNERS,
        createConfig(
            true, Optional.of(EXPECTED_LABEL), owners(USER_A_EMAIL_COM, USER_B_EMAIL_COM)));

    replayAll();

    PathOwners owners2 =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            emptyList(),
            branch,
            Set.of("classic/file.txt"),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.empty(),
            jgitWrapper);
    Set<Account.Id> ownersSet2 = owners2.get().get(CLASSIC_OWNERS);

    // in this case we are inheriting the acct3 from /OWNERS
    assertEquals(3, ownersSet2.size());
    assertTrue(ownersSet2.contains(USER_A_ID));
    assertTrue(ownersSet2.contains(USER_B_ID));
    assertTrue(ownersSet2.contains(USER_C_ID));

    // expect that classic configuration takes precedence over `OWNERS` file for the label
    // definition
    Truth8.assertThat(owners2.getLabel().map(LabelDefinition::getName)).hasValue(EXPECTED_LABEL);
  }

  @Test
  public void testClassicWithInheritanceAndGlobalLabel() throws Exception {
    expectConfig("OWNERS", createConfig(true, Optional.of(A_LABEL), owners()));
    expectConfig(CLASSIC_OWNERS, createConfig(true, Optional.of(B_LABEL), owners()));

    replayAll();

    PathOwners owners2 =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            emptyList(),
            branch,
            Set.of("classic/file.txt"),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.of(EXPECTED_LABEL_DEFINITION),
            jgitWrapper);
    Truth8.assertThat(owners2.getLabel()).hasValue(EXPECTED_LABEL_DEFINITION);
  }

  @Test
  public void testClassicWithoutInheritanceAndGlobalLabel() throws Exception {
    expectConfig("OWNERS", createConfig(false, Optional.of(A_LABEL), owners()));
    expectConfig(CLASSIC_OWNERS, createConfig(false, Optional.of(B_LABEL), owners()));

    replayAll();

    PathOwners owners2 =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            emptyList(),
            branch,
            Set.of("classic/file.txt"),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.of(EXPECTED_LABEL_DEFINITION),
            jgitWrapper);
    Truth8.assertThat(owners2.getLabel()).hasValue(EXPECTED_LABEL_DEFINITION);
  }

  @Test
  public void testRootInheritFromProject() throws Exception {
    expectConfig("OWNERS", "master", createConfig(true, owners()));
    expectConfig(
        "OWNERS",
        RefNames.REFS_CONFIG,
        createConfig(
            true,
            Optional.of(EXPECTED_LABEL),
            owners(),
            suffixMatcher(".sql", USER_A_EMAIL_COM, USER_B_EMAIL_COM)));

    String fileName = "file.sql";
    replayAll();

    PathOwners owners =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            emptyList(),
            branch,
            Set.of(fileName),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.empty(),
            jgitWrapper);

    Map<String, Set<Account.Id>> fileOwners = owners.getFileOwners();
    assertEquals(1, fileOwners.size());

    Set<Account.Id> ownersSet = fileOwners.get(fileName);
    assertEquals(2, ownersSet.size());
    assertTrue(ownersSet.contains(USER_A_ID));
    assertTrue(ownersSet.contains(USER_B_ID));
    Truth8.assertThat(owners.getLabel().map(LabelDefinition::getName)).hasValue(EXPECTED_LABEL);
  }

  @Test
  public void testProjectInheritFromParentProject() throws Exception {
    expectConfig("OWNERS", "master", createConfig(true, Optional.of(EXPECTED_LABEL), owners()));
    expectConfig(
        "OWNERS",
        RefNames.REFS_CONFIG,
        repository,
        createConfig(true, Optional.of("foo"), owners()));
    expectConfig(
        "OWNERS",
        RefNames.REFS_CONFIG,
        parentRepository1,
        createConfig(
            true,
            Optional.of(A_LABEL),
            owners(),
            suffixMatcher(".sql", USER_A_EMAIL_COM, USER_B_EMAIL_COM)));

    String fileName = "file.sql";

    mockParentRepository(parentRepository1NameKey, parentRepository1);
    replayAll();

    PathOwners owners =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            Arrays.asList(parentRepository1NameKey),
            branch,
            Set.of(fileName),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.empty(),
            jgitWrapper);

    Map<String, Set<Account.Id>> fileOwners = owners.getFileOwners();
    assertEquals(fileOwners.size(), 1);

    Set<Account.Id> ownersSet = fileOwners.get(fileName);
    assertEquals(2, ownersSet.size());
    assertTrue(ownersSet.contains(USER_A_ID));
    assertTrue(ownersSet.contains(USER_B_ID));

    // expect that `master` configuration overwrites the label definition of both `refs/meta/config`
    // and parent repo
    Truth8.assertThat(owners.getLabel().map(LabelDefinition::getName)).hasValue(EXPECTED_LABEL);
  }

  @Test
  public void testProjectInheritFromMultipleParentProjects() throws Exception {
    expectConfig("OWNERS", "master", createConfig(true, owners()));
    expectConfig("OWNERS", RefNames.REFS_CONFIG, repository, createConfig(true, owners()));
    expectConfig(
        "OWNERS",
        RefNames.REFS_CONFIG,
        parentRepository1,
        createConfig(
            true, Optional.of(EXPECTED_LABEL), owners(), suffixMatcher(".sql", USER_A_EMAIL_COM)));
    expectConfig(
        "OWNERS",
        RefNames.REFS_CONFIG,
        parentRepository2,
        createConfig(
            true, Optional.of(A_LABEL), owners(), suffixMatcher(".java", USER_B_EMAIL_COM)));

    String sqlFileName = "file.sql";
    String javaFileName = "file.java";

    mockParentRepository(parentRepository1NameKey, parentRepository1);
    mockParentRepository(parentRepository2NameKey, parentRepository2);
    replayAll();

    PathOwners owners =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            Arrays.asList(parentRepository1NameKey, parentRepository2NameKey),
            branch,
            Set.of(sqlFileName, javaFileName),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.empty(),
            jgitWrapper);

    Map<String, Set<Account.Id>> fileOwners = owners.getFileOwners();
    assertEquals(fileOwners.size(), 2);

    Set<Account.Id> ownersSet1 = fileOwners.get(sqlFileName);
    assertEquals(1, ownersSet1.size());
    assertTrue(ownersSet1.contains(USER_A_ID));

    Set<Account.Id> ownersSet2 = fileOwners.get(javaFileName);
    assertEquals(1, ownersSet2.size());
    assertTrue(ownersSet2.contains(USER_B_ID));

    // expect that closer parent (parentRepository1) overwrites the label definition
    Truth8.assertThat(owners.getLabel().map(LabelDefinition::getName)).hasValue(EXPECTED_LABEL);
  }

  private void mockParentRepository(Project.NameKey repositoryName, Repository repository)
      throws IOException {
    expect(repositoryManager.openRepository(eq(repositoryName))).andReturn(repository).anyTimes();
    repository.close();
    expectLastCall();
  }

  @Test
  public void testClassicWithInheritanceAndDeepNesting() throws Exception {
    expectConfig("OWNERS", createConfig(true, owners(USER_C_EMAIL_COM)));
    expectConfig("dir/OWNERS", createConfig(true, Optional.of(A_LABEL), owners(USER_B_EMAIL_COM)));
    expectConfig(
        "dir/subdir/OWNERS",
        createConfig(true, Optional.of(EXPECTED_LABEL), owners(USER_A_EMAIL_COM)));

    replayAll();

    PathOwners owners =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            emptyList(),
            branch,
            Set.of("dir/subdir/file.txt"),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.empty(),
            jgitWrapper);
    Set<Account.Id> ownersSet = owners.get().get("dir/subdir/OWNERS");

    assertEquals(3, ownersSet.size());
    assertTrue(ownersSet.contains(USER_A_ID));
    assertTrue(ownersSet.contains(USER_B_ID));
    assertTrue(ownersSet.contains(USER_C_ID));

    // expect that more specific configuration overwrites the label definition
    Truth8.assertThat(owners.getLabel().map(LabelDefinition::getName)).hasValue(EXPECTED_LABEL);
  }

  @Test
  public void testParsingYamlWithLabelWithScore() throws IOException {
    String yamlString =
        "inherited: true\nlabel: " + EXPECTED_LABEL + ",1\nowners:\n- " + USER_C_EMAIL_COM;
    OwnersConfig ownersConfig = getOwnersConfig(yamlString);

    assertTrue(ownersConfig.isInherited());
    Truth8.assertThat(ownersConfig.getLabel()).isPresent();

    LabelDefinition label = ownersConfig.getLabel().get();
    assertThat(label.getName()).isEqualTo(EXPECTED_LABEL);
    Truth8.assertThat(label.getScore()).hasValue(1);

    Set<String> owners = ownersConfig.getOwners();
    assertEquals(1, owners.size());
    assertTrue(owners.contains(USER_C_EMAIL_COM));
  }

  @Test
  public void testParsingYamlWithLabelWithoutScore() throws IOException {
    String yamlString =
        "inherited: true\nlabel: " + EXPECTED_LABEL + "\nowners:\n- " + USER_C_EMAIL_COM;
    OwnersConfig ownersConfig = getOwnersConfig(yamlString);

    assertTrue(ownersConfig.isInherited());
    Truth8.assertThat(ownersConfig.getLabel()).isPresent();

    LabelDefinition label = ownersConfig.getLabel().get();
    assertThat(label.getName()).isEqualTo(EXPECTED_LABEL);
    Truth8.assertThat(label.getScore()).isEmpty();

    Set<String> owners = ownersConfig.getOwners();
    assertEquals(1, owners.size());
    assertTrue(owners.contains(USER_C_EMAIL_COM));
  }

  @Test
  public void testPathOwnersEntriesCacheIsCalled() throws Exception {
    expectConfig("OWNERS", "master", createConfig(true, Optional.of(EXPECTED_LABEL), owners()));
    expectConfig(
        "OWNERS",
        RefNames.REFS_CONFIG,
        repository,
        createConfig(true, Optional.of("foo"), owners()));
    expectConfig("dir/OWNERS", createConfig(true, Optional.of(A_LABEL), owners(USER_B_EMAIL_COM)));
    expectConfig(
        "dir/subdir/OWNERS",
        createConfig(true, Optional.of(EXPECTED_LABEL), owners(USER_A_EMAIL_COM)));
    expectConfig(
        "OWNERS",
        RefNames.REFS_CONFIG,
        parentRepository1,
        createConfig(true, Optional.of("bar"), owners()));

    mockParentRepository(parentRepository1NameKey, parentRepository1);
    replayAll();

    PathOwnersEntriesCacheMock cacheMock = new PathOwnersEntriesCacheMock();
    PathOwners owners =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            Arrays.asList(parentRepository1NameKey),
            branch,
            Set.of("dir/subdir/file.txt"),
            EXPAND_GROUPS,
            "foo",
            cacheMock,
            Optional.empty(),
            jgitWrapper);

    assertThat(owners.getFileOwners()).isNotEmpty();
    int expectedCacheCalls =
        1 /* for refs/meta/config/OWNERS */
            + 3 /* for each parent directory of 'file.txt' */
            + 1 /* for parent's refs/meta/config/OWNERS */;
    assertThat(cacheMock.hit).isEqualTo(expectedCacheCalls);
  }

  @Test
  public void testAutoOwnersMisconfigured() throws Exception {
    expectConfig("OWNERS", "inherited: true\nauto-owners-approved: \"some wrong value\"");

    replayAll();

    PathOwners owners =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            emptyList(),
            branch,
            Set.of("file.txt"),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.empty(),
            jgitWrapper);

    assertThat(owners.getFileOwnersAllowedAutoApproval()).isEmpty();
    assertThat(owners.getFileOwners()).isEmpty();
  }

  @Test
  public void testAutoOwnersApprovedInheritedFromRoot() throws Exception {
    expectConfig(
        "OWNERS",
        "inherited: true\nauto-owners-approved: true\nowners:\n- " + USER_A_EMAIL_COM + "\n");
    expectConfig("dir/OWNERS", "inherited: true\nowners:\n- " + USER_B_EMAIL_COM + "\n");

    replayAll();

    PathOwners owners =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            emptyList(),
            branch,
            Set.of("dir/file.txt"),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.empty(),
            jgitWrapper);

    assertThat(owners.getFileOwnersAllowedAutoApproval()).contains("dir/file.txt");
  }

  @Test
  public void testAutoOwnersApprovedDefaultsWhenInheritanceStopped() throws Exception {
    expectConfig(
        "OWNERS",
        "inherited: true\nauto-owners-approved: true\nowners:\n- " + USER_A_EMAIL_COM + "\n");
    expectConfig("dir/OWNERS", "inherited: false\nowners:\n- " + USER_B_EMAIL_COM + "\n");

    replayAll();

    PathOwners owners =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            emptyList(),
            branch,
            Set.of("dir/file.txt"),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.empty(),
            jgitWrapper);

    assertThat(owners.getFileOwnersAllowedAutoApproval()).isEmpty();
  }

  @Test
  public void testAutoOwnersApprovedInheritedFromParentProjectOwners() throws Exception {
    expectConfig("OWNERS", "master", createConfig(true, owners()));
    expectConfig("OWNERS", RefNames.REFS_CONFIG, repository, createConfig(true, owners()));
    expectConfig(
        "OWNERS",
        RefNames.REFS_CONFIG,
        parentRepository1,
        "inherited: true\nauto-owners-approved: true\nowners:\n- " + USER_A_EMAIL_COM + "\n");

    mockParentRepository(parentRepository1NameKey, parentRepository1);
    replayAll();

    PathOwners owners =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            Arrays.asList(parentRepository1NameKey),
            branch,
            Set.of("file.txt"),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.empty(),
            jgitWrapper);

    assertThat(owners.getFileOwnersAllowedAutoApproval()).contains("file.txt");
  }

  @Test
  public void testExplicitAutoOwnersApprovedInRootOverridesProjectOwners() throws Exception {
    expectConfig(
        "OWNERS",
        "master",
        "inherited: true\nauto-owners-approved: false\nowners:\n- " + USER_A_EMAIL_COM + "\n");
    expectConfig(
        "OWNERS",
        RefNames.REFS_CONFIG,
        repository,
        "inherited: true\nauto-owners-approved: true\nowners:\n- " + USER_B_EMAIL_COM + "\n");

    replayAll();

    PathOwners owners =
        new PathOwners(
            accounts,
            repositoryManager,
            repository,
            emptyList(),
            branch,
            Set.of("file.txt"),
            EXPAND_GROUPS,
            "foo",
            CACHE_MOCK,
            Optional.empty(),
            jgitWrapper);

    assertThat(owners.getFileOwnersAllowedAutoApproval()).isEmpty();
  }

  private void mockOwners(String... owners) throws IOException {
    expectNoConfig("OWNERS");
    expectConfig(CLASSIC_OWNERS, createConfig(false, owners(owners)));

    replayAll();
  }
}
