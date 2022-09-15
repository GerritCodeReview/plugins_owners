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
import static com.google.common.truth.Truth8.assertThat;
import static com.googlesource.gerrit.owners.common.MatcherConfig.exactMatcher;
import static com.googlesource.gerrit.owners.common.MatcherConfig.partialRegexMatcher;
import static com.googlesource.gerrit.owners.common.MatcherConfig.regexMatcher;
import static com.googlesource.gerrit.owners.common.MatcherConfig.suffixMatcher;
import static com.googlesource.gerrit.owners.common.StreamUtils.iteratorStream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.easymock.PowerMock.replayAll;

import com.google.gerrit.entities.Account;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("jdk.internal.reflect.*")
@PrepareForTest(JgitWrapper.class)
public class RegexTest extends Config {

  private static final String ACCOUNT_A = "a";
  private static final String ACCOUNT_B = "b";
  private static final String ACCOUNT_C = "c";
  private static final String ACCOUNT_D = "d";
  private static final String ACCOUNT_E = "e";
  private static final String ACCOUNT_F = "f";
  private static final Account.Id ACCOUNT_A_ID = Account.id(1);
  private static final Account.Id ACCOUNT_B_ID = Account.id(2);
  private static final Account.Id ACCOUNT_C_ID = Account.id(3);
  private static final Account.Id ACCOUNT_D_ID = Account.id(4);
  private static final Account.Id ACCOUNT_E_ID = Account.id(5);
  private static final Account.Id ACCOUNT_F_ID = Account.id(6);

  @Override
  @Before
  public void setup() throws Exception {
    accounts.put(ACCOUNT_A, ACCOUNT_A_ID);
    accounts.put(ACCOUNT_B, ACCOUNT_B_ID);
    accounts.put(ACCOUNT_C, ACCOUNT_C_ID);
    accounts.put(ACCOUNT_D, ACCOUNT_D_ID);
    accounts.put(ACCOUNT_E, ACCOUNT_E_ID);
    accounts.put(ACCOUNT_F, ACCOUNT_F_ID);

    super.setup();
  }

  @Test
  public void testNewParsingYaml() throws Exception {
    replayAll();

    String fullConfig =
        createConfig(
            true,
            owners(ACCOUNT_A),
            suffixMatcher(".sql", ACCOUNT_B, ACCOUNT_C),
            regexMatcher(".*/a.*", ACCOUNT_D),
            partialRegexMatcher("Product.sql", ACCOUNT_A));
    // the function to test
    Optional<OwnersConfig> configNullable = getOwnersConfig(fullConfig);
    // check classical configuration
    assertThat(configNullable).isPresent();

    OwnersConfig config = configNullable.get();
    assertTrue(config.isInherited());

    Set<String> owners = config.getOwners();
    assertEquals(1, owners.size());
    assertTrue(owners.contains(ACCOUNT_A));
    // check matchers
    Map<String, Matcher> matchers = config.getMatchers();
    assertEquals(3, matchers.size());
    assertTrue(matchers.containsKey(".sql"));
    assertTrue(matchers.containsKey(".*/a.*"));
    assertTrue(matchers.containsKey("Product.sql"));

    // suffix .sql matcher
    Matcher advMatcher = matchers.get(".sql");
    assertEquals(2, advMatcher.getOwners().size());
    Set<Account.Id> advOwners = advMatcher.getOwners();
    assertTrue(advOwners.contains(ACCOUNT_B_ID));
    assertTrue(advOwners.contains(ACCOUNT_C_ID));

    // regex matcher
    Matcher dbMatcher = matchers.get(".*/a.*");
    assertEquals(1, dbMatcher.getOwners().size());
    Set<Account.Id> dbOwners = dbMatcher.getOwners();
    assertTrue(dbOwners.contains(ACCOUNT_D_ID));

    // partial_regex matcher
    Matcher partial = matchers.get("Product.sql");
    assertEquals(1, partial.getOwners().size());
    Set<Account.Id> partialOwners = partial.getOwners();
    assertTrue(partialOwners.contains(ACCOUNT_A_ID));
  }

  @Test
  public void checkMatchers() throws Exception {
    String parentConfig =
        createConfig(
            true,
            owners(ACCOUNT_A),
            suffixMatcher(".sql", ACCOUNT_B, ACCOUNT_C),
            regexMatcher(".*/a.*", ACCOUNT_D));
    String childConfig =
        createConfig(
            true,
            owners(ACCOUNT_F),
            exactMatcher("project/file.txt", ACCOUNT_D, ACCOUNT_E),
            partialRegexMatcher("alfa", ACCOUNT_A));

    expectConfig("OWNERS", parentConfig);
    expectConfig("project/OWNERS", childConfig);

    creatingPatchList(
        Arrays.asList(
            "project/file.txt", // matches exact in
            // project owners d,e
            "file1.txt", // no matches so nothing for this
            "project/afile2.sql", // matches two matchers so we have b,c,d
            "project/bfile.txt", // no matching
            "projectalfa", // matches PartialRegex
            "project/file.sql")); // only .sql matching b,c
    replayAll();

    // function under test
    PathOwners owners = new PathOwners(accounts, Optional.of(allProjectsRepository), repository, branch, patchList);

    // assertions on classic owners
    Set<Account.Id> ownersSet = owners.get().get("project/OWNERS");
    assertEquals(2, ownersSet.size());

    // get matchers
    Map<String, Matcher> matchers = owners.getMatchers();
    assertEquals(4, matchers.size());

    // asserts we have 1 exact matcher
    List<Entry<String, Matcher>> onlyExacts =
        iteratorStream(matchers.entrySet().iterator())
            .filter(entry -> entry.getValue() instanceof ExactMatcher)
            .collect(Collectors.toList());
    assertEquals(1, onlyExacts.size());
    assertEquals("project/file.txt", onlyExacts.get(0).getKey());

    // ... 1 regex matcher
    List<Entry<String, Matcher>> regexList =
        StreamUtils.iteratorStream(matchers.entrySet().iterator())
            .filter(entry -> entry.getValue() instanceof RegExMatcher)
            .collect(Collectors.toList());
    assertEquals(1, regexList.size());
    assertEquals(".*/a.*", regexList.get(0).getKey());

    // ... 1 partial regex matcher
    List<Entry<String, Matcher>> partialRegexList =
        iteratorStream(matchers.entrySet().iterator())
            .filter(entry -> entry.getValue() instanceof PartialRegExMatcher)
            .collect(Collectors.toList());
    assertEquals(1, partialRegexList.size());
    assertEquals("alfa", partialRegexList.get(0).getKey());

    // .... 1 suffix matcher
    List<Entry<String, Matcher>> suffixList =
        iteratorStream(matchers.entrySet().iterator())
            .filter(entry -> entry.getValue() instanceof SuffixMatcher)
            .collect(Collectors.toList());
    assertEquals(1, suffixList.size());
    assertEquals(".sql", suffixList.get(0).getKey());

    // now checks file owners as well
    Map<String, Set<Account.Id>> fileOwners = owners.getFileOwners();
    assertEquals(6, fileOwners.size());

    Set<Account.Id> set1 = fileOwners.get("project/file.txt");
    assertEquals(4, set1.size()); // includes classic owners a and f
    assertTrue(set1.contains(ACCOUNT_A_ID));
    assertTrue(set1.contains(ACCOUNT_D_ID));
    assertTrue(set1.contains(ACCOUNT_E_ID));
    assertTrue(set1.contains(ACCOUNT_F_ID));

    Set<Account.Id> set2 = fileOwners.get("project/afile2.sql");
    assertEquals(5, set2.size());
    assertTrue(set2.contains(ACCOUNT_A_ID));
    assertTrue(set2.contains(ACCOUNT_B_ID));
    assertTrue(set2.contains(ACCOUNT_C_ID));
    assertTrue(set2.contains(ACCOUNT_D_ID));
    assertTrue(set2.contains(ACCOUNT_F_ID));

    Set<Account.Id> set3 = fileOwners.get("project/file.sql");
    assertEquals(4, set3.size());
    assertTrue(set3.contains(ACCOUNT_A_ID));
    assertTrue(set3.contains(ACCOUNT_B_ID));
    assertTrue(set3.contains(ACCOUNT_C_ID));
    assertTrue(set3.contains(ACCOUNT_F_ID));

    Set<Account.Id> set4 = fileOwners.get("projectalfa");
    assertEquals(1, set4.size()); // only 1 because a is class and alfa owner
    assertTrue(set4.contains(ACCOUNT_A_ID));
  }

  @Test
  public void testMatchersOnlyConfig() throws Exception {
    replayAll();

    Optional<OwnersConfig> ownersConfigOpt =
        getOwnersConfig(createConfig(false, new String[0], suffixMatcher(".txt", ACCOUNT_B)));

    assertThat(ownersConfigOpt).isPresent();
    OwnersConfig ownersConfig = ownersConfigOpt.get();

    assertThat(ownersConfig.getOwners()).isEmpty();
    assertThat(ownersConfig.getMatchers()).isNotEmpty();
  }

  @Test
  public void testkRegexShouldMatchOnlyOnSuffix() throws Exception {
    String configString = createConfig(false, new String[0], suffixMatcher(".sql", ACCOUNT_B));

    expectConfig("OWNERS", configString);
    expectNoConfig("project/OWNERS");
    creatingPatch("project/file.sql", "another.txt");
    replayAll();

    PathOwners owners = new PathOwners(accounts, Optional.of(allProjectsRepository), repository, branch, patchList);

    Set<String> ownedFiles = owners.getFileOwners().keySet();
    assertThat(ownedFiles).containsExactly("project/file.sql");
  }
}
