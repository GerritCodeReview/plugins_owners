package com.vmware.gerrit.owners.common;

import com.google.gerrit.reviewdb.client.Account;

import java.util.Set;

public abstract class Matcher {

  @Override
  public String toString() {
    return "Matcher [path=" + path + ", owners=" + owners + "]";
  }

  protected String path;

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

  abstract public boolean matches(String pathToMatch);


  public String getPath() {
    return path;
  }

  public Matcher(String key, Set<Account.Id> owners) {
    this.path = key;
    this.owners = owners;
  }
}
