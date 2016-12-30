package com.vmware.gerrit.owners.common;

import com.google.gerrit.reviewdb.client.Account;

import java.util.Set;

class Matcher {

  @Override
  public String toString() {
    return "Matcher [path=" + path + ", owners=" + owners + "]";
  }

  private String path;

  public Set<Account.Id> getOwners() {
    return owners;
  }

  public void setOwners(Set<Account.Id> owners) {
    this.owners = owners;
  }

  public void setPath(String path) {
    this.path = path;
  }

  private Set<Account.Id> owners;




  public String getPath() {
    return path;
  }

  public Matcher(String key, Set<Account.Id> owners) {
    this.path = key;
    this.owners = owners;
  }
}


class RegExMatcher extends Matcher {
  public RegExMatcher(String path, Set<Account.Id> owners) {
    super(path, owners);
  }
}


class SuffixMatcher extends Matcher {
  public SuffixMatcher(String path, Set<Account.Id> owners) {
    super(path, owners);
  }
}

class ExactMatcher extends Matcher {
  public ExactMatcher(String path, Set<Account.Id> owners ){
    super(path, owners);
  }
}
