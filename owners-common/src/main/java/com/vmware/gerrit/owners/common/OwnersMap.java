package com.vmware.gerrit.owners.common;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;

import java.util.Map;
import java.util.Set;

public class OwnersMap {

  private SetMultimap<String, Account.Id> pathOwners = HashMultimap.create();
  private Map<String,Matcher> matchers = Maps.newHashMap();
  private Map<String,Set<Account.Id>> fileOwners = Maps.newHashMap();

  @Override
  public String toString() {
    return "OwnersMap [pathOwners=" + pathOwners + ", matchers=" + matchers
        + "]";
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
  public Map<String,Matcher> getMatchers() {
    return matchers;
  }
  public void addMatchers(Map<String,Matcher> matchers) {
    this.matchers.putAll(matchers);
  }
  public void addPathOwners(String ownersPath, Set<Id> owners) {
    pathOwners.putAll(ownersPath, owners);
  }
  public Map<String,Set<Id>> getFileOwners() {
    return fileOwners;
  }
  public void addFileOwners(String file, Set<Id> owners) {
    Set<Id> set = fileOwners.get(file);
    if(set!=null) {
      // add new owners removing duplicates
      set.addAll(owners);
    } else {
      fileOwners.put(file, Sets.newHashSet(owners));
    }
  }
}
