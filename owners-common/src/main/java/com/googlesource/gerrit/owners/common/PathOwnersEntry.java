// Copyright (c) 2013 VMware, Inc. All Rights Reserved.
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

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.entities.Account;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Path Owners Entry.
 *
 * <p>Used internally by PathOwners to represent and compute the owners at a specific path.
 */
class PathOwnersEntry extends ReadOnlyPathOwnersEntry {
  static final ReadOnlyPathOwnersEntry EMPTY = new ReadOnlyPathOwnersEntry(true) {};

  public PathOwnersEntry() {
    super(true);
  }

  public PathOwnersEntry(
      String path,
      OwnersConfig config,
      Accounts accounts,
      Optional<LabelDefinition> inheritedLabel,
      Set<Account.Id> inheritedOwners,
      Set<Account.Id> inheritedReviewers,
      Collection<Matcher> inheritedMatchers,
      Set<String> inheritedGroupOwners) {
    super(config.isInherited());
    this.ownersPath = path;
    this.owners =
        config.getOwners().stream()
            .flatMap(o -> accounts.find(o).stream())
            .collect(Collectors.toSet());
    this.reviewers =
        config.getReviewers().stream()
            .flatMap(o -> accounts.find(o).stream())
            .collect(Collectors.toSet());
    this.groupOwners =
        config.getOwners().stream()
            .map(PathOwnersEntry::stripOwnerDomain)
            .collect(Collectors.toSet());
    this.matchers = config.getMatchers();

    if (config.isInherited()) {
      this.owners.addAll(inheritedOwners);
      this.groupOwners.addAll(inheritedGroupOwners);
      this.reviewers.addAll(inheritedReviewers);
      for (Matcher matcher : inheritedMatchers) {
        addMatcher(matcher);
      }
      this.label = config.getLabel().or(() -> inheritedLabel);
    } else {
      this.label = config.getLabel();
    }
  }

  public void addMatcher(Matcher matcher) {
    Matcher currMatchers = this.matchers.get(matcher.getPath());
    this.matchers.put(matcher.getPath(), matcher.merge(currMatchers));
  }

  public void setOwners(Set<Account.Id> owners) {
    this.owners = owners;
  }

  public void setReviewers(Set<Account.Id> reviewers) {
    this.reviewers = reviewers;
  }

  public void setOwnersPath(String ownersPath) {
    this.ownersPath = ownersPath;
  }

  public void setMatchers(Map<String, Matcher> matchers) {
    this.matchers = matchers;
  }

  public void setLabel(Optional<LabelDefinition> label) {
    this.label = label;
  }

  public void addMatchers(Collection<Matcher> values) {
    for (Matcher matcher : values) {
      addMatcher(matcher);
    }
  }

  @Override
  protected String className() {
    return getClass().getSimpleName();
  }
}

abstract class ReadOnlyPathOwnersEntry {
  protected final boolean inherited;
  protected Optional<LabelDefinition> label;
  protected Set<Account.Id> owners = Sets.newHashSet();
  protected Set<Account.Id> reviewers = Sets.newHashSet();
  protected String ownersPath;
  protected Map<String, Matcher> matchers = Maps.newHashMap();
  protected Set<String> groupOwners = Sets.newHashSet();

  protected ReadOnlyPathOwnersEntry(boolean inherited) {
    this.inherited = inherited;
    label = Optional.empty();
  }

  public Map<String, Matcher> getMatchers() {
    return matchers;
  }

  public Set<Account.Id> getOwners() {
    return owners;
  }

  public Set<String> getGroupOwners() {
    return groupOwners;
  }

  public Set<Account.Id> getReviewers() {
    return reviewers;
  }

  public String getOwnersPath() {
    return ownersPath;
  }

  public boolean isInherited() {
    return inherited;
  }

  public Optional<LabelDefinition> getLabel() {
    return label;
  }

  public boolean hasMatcher(String path) {
    return this.matchers.containsKey(path);
  }

  public static String stripOwnerDomain(String owner) {
    return Splitter.on('@').split(owner).iterator().next();
  }

  @Override
  public String toString() {
    return className()
        + " [ownersPath="
        + ownersPath
        + ", owners="
        + owners
        + ", matchers="
        + matchers
        + ", label="
        + label
        + "]";
  }

  protected String className() {
    return "ReadOnlyPathOwnersEntry";
  }
}
