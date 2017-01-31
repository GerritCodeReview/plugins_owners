package com.vmware.gerrit.owners.common;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;

import java.util.Set;
import java.util.regex.Pattern;

// Javascript like regular expression matching substring
public class PartialRegExMatcher extends Matcher {
  Pattern pattern;
  public PartialRegExMatcher(String path, Set<Account.Id> owners) {
    super(path, owners);
    pattern = Pattern.compile(".*"+path+".*");

  }
  @Override
  public boolean matches(String pathToMatch) {
    return pattern.matcher(pathToMatch).matches();
  }

}