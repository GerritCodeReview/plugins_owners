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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.Repository;
import org.junit.Ignore;
import org.powermock.api.easymock.PowerMock;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

@Ignore
public abstract class Config {
  protected ReviewDb db;
  protected Repository repository;
  protected AccountResolver resolver;
  protected PatchList patchList;
  protected Map<String, Account.Id> accounts = Maps.newHashMap();
  protected ConfigurationParser parser;

  public void setup() throws Exception {
    PowerMock.mockStatic(JgitWrapper.class);

    db = PowerMock.createMock(ReviewDb.class);
    repository = PowerMock.createMock(Repository.class);
    resolver = PowerMock.createMock(AccountResolver.class);
    parser = new ConfigurationParser(resolver, db);
    resolvingEmailToAccountIdMocking();
  }



  abstract void resolvingEmailToAccountIdMocking() throws Exception;

  void expectWrapper(String path, @Nonnull Optional<byte[]> value)
      throws IOException {
    expect(JgitWrapper.getBlobAsBytes(anyObject(Repository.class),
        anyObject(String.class), eq(path))).andReturn(value).anyTimes();
  }

  void resolveEmail(String email, int value) throws OrmException {
    Account.Id acct = new Account.Id(value);
    Set<Account.Id> set = new HashSet<>();
    set.add(acct);
    expect(resolver.findAll(anyObject(ReviewDb.class), eq(email)))
        .andReturn(set).anyTimes();
    accounts.put(email, acct);
  }

  void creatingPatchList(List<String> names) {
    patchList = PowerMock.createMock(PatchList.class);
    List<PatchListEntry> entries = names.stream()
        .map(name -> expectEntry(name)).collect(Collectors.toList());
    expect(patchList.getPatches()).andReturn(entries);
  }

  PatchListEntry expectEntry(String name) {
    PatchListEntry entry = PowerMock.createMock(PatchListEntry.class);
    expect(entry.getNewName()).andReturn(name).anyTimes();
    expect(entry.getChangeType()).andReturn(Patch.ChangeType.MODIFIED)
        .anyTimes();
    expect(entry.getDeletions()).andReturn(1);
    expect(entry.getInsertions()).andReturn(1);
    return entry;
  }

  Optional<OwnersConfig> getOwnersConfig(String string) {
    return parser.getOwnersConfig(string.getBytes(Charsets.UTF_8));
  }
}
