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

import com.google.gerrit.entities.Account;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.Ignore;

@Ignore
public class TestAccounts extends HashMap<String, Account.Id> implements Accounts {
  private static final long serialVersionUID = 1L;

  @Override
  public Set<Account.Id> find(String nameOrEmail) {
    return Optional.ofNullable(get(nameOrEmail))
        .map(id -> new HashSet<>(Arrays.asList(id)))
        .orElse(new HashSet<Account.Id>());
  }
}
