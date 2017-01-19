package com.vmware.gerrit.owners.common;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.powermock.api.easymock.PowerMock.replayAll;

import com.google.common.collect.Maps;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.patch.ComparisonType;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Repository;
import org.powermock.api.easymock.PowerMock;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

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

  void creatingPatchList(String name, String name2) {
    patchList = PowerMock.createMock(PatchList.class);
    PatchListEntry entry = expectEntry(name);
    if (name2 != null) {
      PatchListEntry entry2 = expectEntry(name2);
      expect(patchList.getPatches()).andReturn(Arrays.asList(entry,entry2));
    } else {
      expect(patchList.getPatches()).andReturn(Arrays.asList(entry));
    }
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
}
