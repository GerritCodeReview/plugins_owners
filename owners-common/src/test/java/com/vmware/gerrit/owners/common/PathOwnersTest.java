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

package com.vmware.gerrit.owners.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.easymock.PowerMock.replayAll;

import com.google.gerrit.reviewdb.client.Account;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(JgitWrapper.class)
public class PathOwnersTest extends ClassicConfig {

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();
  }

  @Test
  public void testClassic() throws Exception {
    expectNoConfig("OWNERS");
    expectConfig("classic/OWNERS", createConfig(false, owners(USER_A_EMAIL_COM, USER_B_EMAIL_COM)));

    creatingPatchList(Arrays.asList("classic/file.txt"));
    replayAll();

    PathOwners owners = new PathOwners(accounts, repository, branch, patchList);
    Set<Account.Id> ownersSet = owners.get().get("classic/OWNERS");
    assertEquals(2, ownersSet.size());
    assertTrue(ownersSet.contains(USER_A_ID));
    assertTrue(ownersSet.contains(USER_B_ID));
  }

  @Test
  public void testClassicWithInheritance() throws Exception {
    expectConfig("OWNERS", createConfig(true, owners(USER_C_EMAIL_COM)));
    expectConfig("classic/OWNERS", createConfig(true, owners(USER_A_EMAIL_COM, USER_B_EMAIL_COM)));

    creatingPatchList(Arrays.asList("classic/file.txt"));
    replayAll();

    PathOwners owners2 = new PathOwners(accounts, repository, branch, patchList);
    Set<Account.Id> ownersSet2 = owners2.get().get("classic/OWNERS");

    // in this case we are inheriting the acct3 from /OWNERS
    assertEquals(3, ownersSet2.size());
    assertTrue(ownersSet2.contains(USER_A_ID));
    assertTrue(ownersSet2.contains(USER_B_ID));
    assertTrue(ownersSet2.contains(USER_C_ID));
  }

  @Test
  public void testParsingYaml() {
    String yamlString = ("inherited: true\nowners:\n- " + USER_C_EMAIL_COM);
    Optional<OwnersConfig> config = getOwnersConfig(yamlString);
    assertTrue(config.isPresent());
    assertEquals(1, config.get().getOwners().size());
    assertTrue(config.get().getOwners().contains(USER_C_EMAIL_COM));
  }
}
