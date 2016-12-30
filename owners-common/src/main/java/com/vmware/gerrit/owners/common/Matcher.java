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

import java.util.Set;
import java.util.regex.Pattern;

public abstract class Matcher {
  private Set<Account.Id> owners;
  protected String path;

  public Matcher(String key, Set<Account.Id> owners) {
    this.path = key;
    this.owners = owners;
  }

  @Override
  public String toString() {
    return "Matcher [path=" + path + ", owners=" + owners + "]";
  }

  public Set<Account.Id> getOwners() {
    return owners;
  }

  public void setOwners(Set<Account.Id> owners) {
    this.owners = owners;
  }

  public void setPath(String path) {
    this.path = path;
  }


  public String getPath() {
    return path;
  }

  abstract public boolean matches(String pathToMatch);
}


class RegExMatcher extends Matcher {
  final Pattern pattern;

  public RegExMatcher(String path, Set<Account.Id> owners) {
    super(path, owners);
    pattern = Pattern.compile(path);

  }
  @Override
  public boolean matches(String pathToMatch) {
    return pattern.matcher(pathToMatch).matches();
  }
}

class SuffixMatcher extends Matcher {
  public SuffixMatcher(String path, Set<Account.Id> owners) {
    super(path, owners);
  }

  @Override
  public boolean matches(String pathToMatch) {
    return pathToMatch.endsWith(path);
  }
}

class ExactMatcher extends Matcher {
  public ExactMatcher(String path, Set<Account.Id> owners ){
    super(path, owners);
  }

  @Override
  public boolean matches(String pathToMatch) {
    return pathToMatch.equals(path);
  }
}
