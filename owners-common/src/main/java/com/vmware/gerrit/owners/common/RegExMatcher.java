package com.vmware.gerrit.owners.common;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;

import java.util.Set;
import java.util.regex.Pattern;

public class RegExMatcher extends Matcher {
  Pattern pattern;
  public RegExMatcher(String path, Set<Account.Id> owners) {
    super(path, owners);
    pattern = Pattern.compile(path);

  }
  @Override
  public boolean matches(String pathToMatch) {
    return pattern.matcher(pathToMatch).matches();
  }

}