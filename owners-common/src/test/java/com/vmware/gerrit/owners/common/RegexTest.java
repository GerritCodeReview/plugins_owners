package com.vmware.gerrit.owners.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.easymock.PowerMock.replayAll;

import com.google.common.base.Charsets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gwtorm.server.OrmException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(PowerMockRunner.class)
@PrepareForTest(JgitWrapper.class)
public class RegexTest extends RegexConfig {

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();
  }

  @Test
  public void testNewParsingYaml() throws Exception {
    replayAll();

    String fullConfig = createConfig(true, new String[] {"a"},
        new Matcher[] {
            new SuffixMatcher(".sql",
                convertEmailsToIds(new String[] {"b", "c"})),
            new RegExMatcher(".*/a.*", convertEmailsToIds(new String[] {"d"})),
            new PartialRegExMatcher("Product.sql",
                convertEmailsToIds(new String[] {"a"}))});
    // the function to test
    Optional<OwnersConfig> configNullable = getOwnersConfig(fullConfig);
    // check classical configuration
    assertTrue(configNullable.isPresent());

    OwnersConfig config = configNullable.get();
    assertTrue(config.isInherited());

    Set<String> owners = config.getOwners();
    assertEquals(1, owners.size());
    assertTrue(owners.contains("a"));
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
    assertTrue(advOwners.contains(mapping.inverse().get("b")));
    assertTrue(advOwners.contains(mapping.inverse().get("c")));

    // regex matcher
    Matcher dbMatcher = matchers.get(".*/a.*");
    assertEquals(1, dbMatcher.getOwners().size());
    Set<Account.Id> dbOwners = dbMatcher.getOwners();
    assertTrue(dbOwners.contains(mapping.inverse().get("d")));


    // partial_regex matcher
    Matcher partial = matchers.get("Product.sql");
    assertEquals(1, partial.getOwners().size());
    Set<Account.Id> partialOwners = partial.getOwners();
    assertTrue(partialOwners.contains(mapping.inverse().get("a")));
  }

  @Test
  public void checkMatchers() throws Exception {

    String parentConfig = createConfig(true, new String[] {"a"}, new Matcher[] {
        new SuffixMatcher(".sql", convertEmailsToIds(new String[] {"b", "c"})),
        new RegExMatcher(".*/a.*", convertEmailsToIds(new String[] {"d"}))});
    String childConfig = createConfig(true, new String[] {"f"},
        new Matcher[] {
            new ExactMatcher("project/file.txt",
                convertEmailsToIds(new String[] {"d", "e"})),
            new PartialRegExMatcher("alfa",
                convertEmailsToIds(new String[] {"a"}))});

    expectWrapper("OWNERS", Optional.of(parentConfig.getBytes()));
    expectWrapper("project/OWNERS", Optional.of(childConfig.getBytes()));

    creatingPatchList(Arrays.asList(
        "project/file.txt", // matches exact in project owners d,e
        "file1.txt", // no matches so nothing for this
        "project/afile2.sql", // matches two matchers so we have b,c,d
        "project/bfile.txt",  // no matching
        "projectalfa", // matches PartialRegex
        "project/file.sql")); // only .sql matching b,c
    replayAll();

    // function under test
    PathOwners owners = new PathOwners(resolver, db, repository, patchList);

    // assertions on classic owners
    Set<Account.Id> ownersSet = owners.get().get("project/OWNERS");
    assertEquals(2, ownersSet.size());

    // get matches
    Map<String, Matcher> matches = owners.getMatches();
    assertEquals(4, matches.size());

    // asserts we have 1 exact matcher
    List<Entry<String, Matcher>> onlyExacts =
        StreamUtils.asStream(matches.entrySet().iterator())
            .filter(entry -> entry.getValue() instanceof ExactMatcher)
            .collect(Collectors.toList());
    assertEquals(1, onlyExacts.size());
    assertEquals("project/file.txt", onlyExacts.get(0).getKey());

    // ... 1 regex matcher
    List<Entry<String, Matcher>> regexList =
        StreamUtils.asStream(matches.entrySet().iterator())
            .filter(entry -> entry.getValue() instanceof RegExMatcher)
            .collect(Collectors.toList());
    assertEquals(1, regexList.size());
    assertEquals(".*/a.*", regexList.get(0).getKey());

    // ... 1 partial regex matcher
    List<Entry<String, Matcher>> partialRegexList =
        StreamUtils.asStream(matches.entrySet().iterator())
            .filter(entry -> entry.getValue() instanceof PartialRegExMatcher)
            .collect(Collectors.toList());
    assertEquals(1, partialRegexList.size());
    assertEquals("alfa", partialRegexList.get(0).getKey());

    // .... 1 suffix matcher
    List<Entry<String, Matcher>> suffixList =
        StreamUtils.asStream(matches.entrySet().iterator())
            .filter(entry -> entry.getValue() instanceof SuffixMatcher)
            .collect(Collectors.toList());
    assertEquals(1, suffixList.size());
    assertEquals(".sql", suffixList.get(0).getKey());

    // now checks file owners as well
    Map<String, Set<Id>> fileOwners = owners.getFileOwners();
    assertEquals(6,fileOwners.size());

    Set<Id> set1 = fileOwners.get("project/file.txt");
    assertEquals(4,set1.size()); // includes classic owners a and f
    assertTrue(set1.contains(mapping.inverse().get("a")));
    assertTrue(set1.contains(mapping.inverse().get("d")));
    assertTrue(set1.contains(mapping.inverse().get("e")));
    assertTrue(set1.contains(mapping.inverse().get("f")));

    Set<Id> set2 = fileOwners.get("project/afile2.sql");
    assertEquals(5,set2.size());
    assertTrue(set2.contains(mapping.inverse().get("a")));
    assertTrue(set2.contains(mapping.inverse().get("b")));
    assertTrue(set2.contains(mapping.inverse().get("c")));
    assertTrue(set2.contains(mapping.inverse().get("d")));
    assertTrue(set2.contains(mapping.inverse().get("f")));

    Set<Id> set3 = fileOwners.get("project/file.sql");
    assertEquals(4,set3.size());
    assertTrue(set3.contains(mapping.inverse().get("a")));
    assertTrue(set3.contains(mapping.inverse().get("b")));
    assertTrue(set3.contains(mapping.inverse().get("c")));
    assertTrue(set3.contains(mapping.inverse().get("f")));

    Set<Id> set4 = fileOwners.get("projectalfa");
    assertEquals(1,set4.size()); // only 1 because a is class and alfa owner
    assertTrue(set4.contains(mapping.inverse().get("a")));


  }
}
