/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners.common;

import com.google.common.collect.Maps;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gwt.thirdparty.guava.common.collect.Sets;

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
  private String ownersPath;
  private Set<Account.Id> owners;
  private Map<String, Matcher> matchers = Maps.newHashMap();

  public PathOwnersEntry() {
    owners = Sets.newHashSet();
  }

  public String getOwnersPath() {
    return ownersPath;
  }

  public void setOwnersPath(String ownersPath) {
    this.ownersPath = ownersPath;
  }

  public Set<Account.Id> getOwners() {
    return owners;
  }

  public void addOwners(Collection<Account.Id> owners) {
    this.owners.addAll(owners);
  }

  public void addMatchers(Map<String,Matcher>matchers) {
    this.matchers.putAll(matchers);
  }

  public Map<String, Matcher> getMatchers() {
    return matchers;
  }

  public void setMatchers(Map<String, Matcher> matchers) {
    this.matchers = matchers;
  }
}
