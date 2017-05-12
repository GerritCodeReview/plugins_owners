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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.ExternalId;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class AccountsImpl implements Accounts {
  private static final Logger log = LoggerFactory.getLogger(AccountsImpl.class);

  private ReviewDb db;
  private AccountResolver resolver;
  private final AccountCache byId;

  @Inject
  public AccountsImpl(AccountResolver resolver, AccountCache byId, ReviewDb db) {
    this.resolver = resolver;
    this.byId = byId;
    this.db = db;
  }

  @Override
  public Set<Account.Id> find(String nameOrEmail) {
    try {
      Set<Id> accountIds = resolver.findAll(db, nameOrEmail);
      if (accountIds.isEmpty()) {
        log.warn("User '{}' does not resolve to any account.", nameOrEmail);
        return accountIds;
      }

      Set<Id> fulllyMatchedAccountIds =
          accountIds
              .stream()
              .filter(id -> isFullMatch(id, nameOrEmail))
              .collect(Collectors.toSet());
      if (fulllyMatchedAccountIds.isEmpty()) {
        log.warn(
            "User '{}' resolves to {} accounts {}, but does not correspond to any them",
            nameOrEmail,
            accountIds.size(),
            accountIds);
        return fulllyMatchedAccountIds;
      }

      return accountIds;
    } catch (OrmException e) {
      log.error("Error trying to resolve user " + nameOrEmail, e);
      return Collections.emptySet();
    }
  }

  private boolean isFullMatch(Account.Id id, String nameOrEmail) {
    AccountState account = byId.get(id);
    return account.getAccount().getFullName().trim().equalsIgnoreCase(nameOrEmail)
        || account
            .getExternalIds()
            .stream()
            .anyMatch(extId -> getSchemeRest(extId.key()).trim().equalsIgnoreCase(nameOrEmail));
  }

  private String getSchemeRest(ExternalId.Key key) {
    String scheme = key.scheme();
    return null != scheme ? key.get().substring(scheme.length() + 1) : null;
  }
}
