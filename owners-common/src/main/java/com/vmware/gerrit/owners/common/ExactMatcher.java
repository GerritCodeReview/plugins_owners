package com.vmware.gerrit.owners.common;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;

import java.util.Set;

public class ExactMatcher extends Matcher {
  public ExactMatcher(String path, Set<Account.Id> owners ){
    super(path, owners);
  }

  @Override
  public boolean matches(String pathToMatch) {
    return pathToMatch.equals(path);
  }
}