/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners.common;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Path Owners Entry.
 * <p/>
 * Used internally by PathOwners to represent and compute the owners at a
 * specific path.
 */
class PathOwnersEntry {
  @Override
  public String toString() {
    return "PathOwnersEntry [ownersPath=" + ownersPath + ", owners=" + owners
        + ", matchers=" + matchers + "]";
  }

  private String ownersPath;
  private Set<Account.Id> owners = Sets.newHashSet();

  private Map<String, Matcher> matchers = Maps.newHashMap();


  public void addMatcher(Matcher matcher) {
    this.matchers.put(matcher.getPath(), matcher);
  }

  public Map<String, Matcher> getMatchers() {
    return matchers;
  }

  public Set<Account.Id>getOwners() {
    return owners;
  }

  public void setOwners(Set<Account.Id>owners) {
    this.owners = owners;
  }

  public String getOwnersPath() {
    return ownersPath;
  }

  public void setOwnersPath(String ownersPath) {
    this.ownersPath = ownersPath;
  }

  public void setMatchers(Map<String, Matcher> matchers) {
    this.matchers = matchers;
  }
}
