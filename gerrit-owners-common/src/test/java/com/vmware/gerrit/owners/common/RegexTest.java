package com.vmware.gerrit.owners.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.easymock.PowerMock.replayAll;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gwtorm.server.OrmException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(PowerMockRunner.class)
@PrepareForTest(JgitWrapper.class)
public class RegexTest {

  RegexConfig helper = new RegexConfig();

  @Before
  public void setup() throws Exception {
    helper.setup();
  }

  @Test
  public void testNewParsingYaml() throws Exception {

    replayAll();
    String yamlString = helper.advancedConfigFull();

    Optional<OwnersConfig> configNullable = helper.parser.parseYaml("",yamlString);
    assertTrue(configNullable.isPresent());
    OwnersConfig config = configNullable.get();
    Set<String> owners = config.getOwners();
    assertEquals(2, owners.size());
    assertTrue(owners.contains("jane@email.com"));
    assertTrue(owners.contains("john@email.com"));

    Map<String, Matcher> matchers = config.getMatchers();

    assertEquals(2, matchers.size());
    assertTrue(matchers.containsKey(".sql"));
    assertTrue(matchers.containsKey("Product.scala"));

    Matcher advMatcher = matchers.get("Product.scala");
    assertEquals(2, advMatcher.getOwners().size());
    Set<Account.Id> advOwners = advMatcher.getOwners();
    assertTrue(advOwners.contains(helper.accounts.get("bob@email.com")));
    assertTrue(advOwners.contains(helper.accounts.get("alice@email.com")));


    Matcher dbMatcher = matchers.get(".sql");

    assertEquals(2, dbMatcher.getOwners().size());
    Set<Account.Id> dbOwners = dbMatcher.getOwners();
    assertTrue(dbOwners.contains(helper.accounts.get("philip@email.com")));
    assertTrue(dbOwners.contains(helper.accounts.get("frank@email.com")));


  }

  @Test
  public void checkMatchers() throws Exception {
    helper.configureMatchers();
    replayAll();
    PathOwners owners = new PathOwners(helper.resolver, helper.db, helper.repository, helper.patchList);

    Set<Account.Id> ownersSet = owners.get().get("/project/OWNERS");

    assertEquals(2,ownersSet.size());

    Map<String, Matcher> matches = owners.getMatches();

    List<Entry<String, Matcher>> onlyExacts = StreamUtils.asStream(matches.entrySet().iterator())
      .filter( entry -> entry.getValue() instanceof ExactMatcher)
      .collect(Collectors.toList());

    assertEquals(1,onlyExacts.size());
    assertEquals("Product.scala", onlyExacts.get(0).getValue().getPath());

    assertEquals(4,matches.size());

    matches.forEach((key,value) -> {
      final StringBuffer buf = new StringBuffer("key:" + key +"\n" +
      "path:" + value.getPath() + "\n" +
       "owners: ");
      value.getOwners().forEach(account -> {
        buf.append(account.toString() + " ");
      });
      buf.append("\n");
      System.out.println(buf);
    });



  }



}
