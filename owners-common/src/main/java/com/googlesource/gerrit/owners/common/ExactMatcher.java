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
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners.common;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Account.Id;
import java.util.Set;

public class ExactMatcher extends Matcher {
  public ExactMatcher(
      String path, Set<Account.Id> owners, Set<Account.Id> reviewers, Set<String> groupOwners) {
    super(path, owners, reviewers, groupOwners);
  }

  @Override
  public boolean matches(String pathToMatch) {
    return pathToMatch.equals(path);
  }

  @Override
  protected Matcher clone(Set<Id> owners, Set<Id> reviewers, Set<String> groupOwners) {
    return new ExactMatcher(path, owners, reviewers, groupOwners);
  }
}
