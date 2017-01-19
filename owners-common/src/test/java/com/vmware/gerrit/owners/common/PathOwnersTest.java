// Copyright (C) 2013 The Android Open Source Project
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

package com.vmware.gerrit.owners.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Charsets;
import com.google.gerrit.reviewdb.client.Account;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Optional;
import java.util.Set;

@RunWith(PowerMockRunner.class)
@PrepareForTest(JgitWrapper.class)
public class PathOwnersTest  {
  ClassicConfig helper = new ClassicConfig();

  @Before
  public void setup() throws Exception {
    helper.setup();
  }

  @Test
  public void testClassic() throws Exception {
    helper.configurationClassic(false);
    PathOwners owners = new PathOwners(helper.resolver, helper.db, helper.repository, helper.patchList);
    Set<Account.Id> ownersSet = owners.get().get("/classic/OWNERS");
    assertEquals(2, ownersSet.size());
    assertTrue(ownersSet.contains(helper.accounts.get("user-a@email.com")));
    assertTrue(ownersSet.contains(helper.accounts.get("user-b@email.com")));
  }

  @Test
  public void testClassicWithInheritance() throws Exception {
    helper.configurationClassic(true);
    PathOwners owners2 = new PathOwners(helper.resolver, helper.db, helper.repository, helper.patchList);
    Set<Account.Id> ownersSet2 = owners2.get().get("/classic/OWNERS");

    // in this case we are inheriting the acct3 from /OWNERS
    assertEquals(3, ownersSet2.size());
    assertTrue(ownersSet2.contains(helper.accounts.get("user-a@email.com")));
    assertTrue(ownersSet2.contains(helper.accounts.get("user-b@email.com")));
    assertTrue(ownersSet2.contains(helper.accounts.get("user-c@email.com")));
  }

  @Test
  public void testClassicBis() throws Exception {
    helper.configurationClassicBis();
    PathOwners owners = new PathOwners(helper.resolver, helper.db, helper.repository, helper.patchList);
    System.out.println(owners);
  }

  @Test
  public void testParsingYaml() {
    String yamlString = (
        "inherited: true\n" +
        "owners:\n" +
        "- user-c@example.com");
    Optional<OwnersConfig> config = getOwnersConfig(yamlString);
    assertTrue(config.isPresent());
    assertEquals(1,config.get().getOwners().size());
    assertTrue(config.get().getOwners().contains("user-c@example.com"));
  }

  Optional<OwnersConfig> getOwnersConfig(String string) {
    return new ConfigurationParser().getOwnersConfig(string.getBytes(Charsets.UTF_8));
  }
}
