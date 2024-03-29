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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Account.Id;
import java.util.Set;

public abstract class Matcher {
  private Set<Account.Id> owners;
  private Set<Account.Id> reviewers;
  private Set<String> groupOwners;
  protected String path;

  public Matcher(
      String key, Set<Account.Id> owners, Set<Account.Id> reviewers, Set<String> groupOwners) {
    this.path = key;
    this.owners = owners;
    this.reviewers = reviewers;
    this.groupOwners = groupOwners;
  }

  @Override
  public String toString() {
    return "Matcher [path="
        + path
        + ", owners="
        + owners
        + ", groupOwners="
        + groupOwners
        + ", reviewers="
        + reviewers
        + "]";
  }

  public Set<Account.Id> getOwners() {
    return owners;
  }

  public Set<String> getGroupOwners() {
    return groupOwners;
  }

  public void setOwners(Set<Account.Id> owners) {
    this.owners = owners;
  }

  public Set<Account.Id> getReviewers() {
    return reviewers;
  }

  public void setReviewers(Set<Account.Id> reviewers) {
    this.reviewers = reviewers;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getPath() {
    return path;
  }

  public abstract boolean matches(String pathToMatch);

  public Matcher merge(Matcher other) {
    if (other == null) {
      return this;
    }

    return clone(
        mergeSet(owners, other.owners),
        mergeSet(reviewers, other.reviewers),
        mergeSet(groupOwners, other.groupOwners));
  }

  protected abstract Matcher clone(Set<Id> owners, Set<Id> reviewers, Set<String> groupOwners);

  private <T> Set<T> mergeSet(Set<T> set1, Set<T> set2) {
    ImmutableSet.Builder<T> setBuilder = ImmutableSet.builder();
    return setBuilder.addAll(set1).addAll(set2).build();
  }
}
