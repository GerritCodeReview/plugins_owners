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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Account.Id;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class OwnersMap {
  private SetMultimap<String, Account.Id> pathOwners = HashMultimap.create();
  private SetMultimap<String, Account.Id> pathReviewers = HashMultimap.create();
  private Map<String, Matcher> matchers = Maps.newHashMap();
  private Map<String, Set<Account.Id>> fileOwners = Maps.newHashMap();
  private Map<String, Set<Account.Id>> fileReviewers = Maps.newHashMap();
  private Map<String, Set<String>> fileGroupOwners = Maps.newHashMap();
  private Optional<LabelDefinition> label = Optional.empty();

  @Override
  public String toString() {
    return "OwnersMap [pathOwners=" + pathOwners + ", matchers=" + matchers + "]";
  }

  public void setMatchers(Map<String, Matcher> matchers) {
    this.matchers = matchers;
  }

  public SetMultimap<String, Account.Id> getPathOwners() {
    return pathOwners;
  }

  public void setPathOwners(SetMultimap<String, Account.Id> pathOwners) {
    this.pathOwners = pathOwners;
  }

  public SetMultimap<String, Account.Id> getPathReviewers() {
    return pathReviewers;
  }

  public void setPathReviewers(SetMultimap<String, Account.Id> pathReviewers) {
    this.pathReviewers = pathReviewers;
  }

  public Map<String, Matcher> getMatchers() {
    return matchers;
  }

  public void addMatchers(Map<String, Matcher> matchers) {
    this.matchers.putAll(matchers);
  }

  public void addPathOwners(String ownersPath, Set<Id> owners) {
    pathOwners.putAll(ownersPath, owners);
  }

  public void addPathReviewers(String ownersPath, Set<Id> reviewers) {
    pathOwners.putAll(ownersPath, reviewers);
  }

  public Map<String, Set<Id>> getFileOwners() {
    return fileOwners;
  }

  public Map<String, Set<Id>> getFileReviewers() {
    return fileReviewers;
  }

  public Map<String, Set<String>> getFileGroupOwners() {
    return fileGroupOwners;
  }

  public void addFileOwners(String file, Set<Id> owners) {
    if (owners.isEmpty()) {
      return;
    }

    Set<Id> set = fileOwners.get(file);
    if (set != null) {
      // add new owners removing duplicates
      set.addAll(owners);
    } else {
      fileOwners.put(file, Sets.newHashSet(owners));
    }
  }

  public void addFileReviewers(String file, Set<Id> reviewers) {
    if (reviewers.isEmpty()) {
      return;
    }

    Set<Id> set = fileReviewers.get(file);
    if (set != null) {
      // add new owners removing duplicates
      set.addAll(reviewers);
    } else {
      fileReviewers.put(file, Sets.newHashSet(reviewers));
    }
  }

  public void addFileGroupOwners(String file, Set<String> groupOwners) {
    if (groupOwners.isEmpty()) {
      return;
    }

    fileGroupOwners.computeIfAbsent(file, (f) -> Sets.newHashSet()).addAll(groupOwners);
  }

  public Optional<LabelDefinition> getLabel() {
    return label;
  }

  public void setLabel(Optional<LabelDefinition> label) {
    this.label = label;
  }
}
