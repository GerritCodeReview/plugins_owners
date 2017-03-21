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

import java.util.Collections;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

public class AccountsImpl implements Accounts {
  private static final Logger log = LoggerFactory.getLogger(AccountsImpl.class);

  private ReviewDb db;
  private AccountResolver resolver;

  @Inject
  public AccountsImpl(AccountResolver resolver, ReviewDb db) {
    this.resolver = resolver;
    this.db = db;
  }

  @Override
  public Set<Account.Id> find(String nameOrEmail) {
    Set<Id> accountIds = Collections.emptySet();
    try {
      accountIds = resolver.findAll(db, nameOrEmail);
    } catch (OrmException e) {
      log.error("Error trying to resolve user " + nameOrEmail, e);
    }
    if (accountIds.isEmpty()) {
      log.warn("User {} does not correspond to any account id", nameOrEmail);
    }
    return accountIds;
  }
}
