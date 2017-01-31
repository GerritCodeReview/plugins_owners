package com.vmware.gerrit.owners.common;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gwt.thirdparty.guava.common.collect.Maps;
import com.google.gwtorm.server.OrmException;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RegexConfig extends Config {

  final static BiMap<Account.Id,String> mapping = HashBiMap.create();
  static {
    mapping.put(new Account.Id(1), "a");
    mapping.put(new Account.Id(2), "b");
    mapping.put(new Account.Id(3), "c");
    mapping.put(new Account.Id(4), "d");
    mapping.put(new Account.Id(5), "e");
    mapping.put(new Account.Id(6), "f");
  }

  public String createConfig(boolean inherited, String[] owners,
      Matcher[] matchers) {
    StringBuilder sb = new StringBuilder();
    sb.append("inherited: " + inherited + "\n");
    sb.append("owners: \n");
    for (String owner : owners) {
      sb.append("- " + owner + "\n");
    }
    if (matchers.length > 0) {
      sb.append("matches: \n");
      for (Matcher matcher : matchers) {
        if (matcher instanceof RegExMatcher) {
          sb.append("- regex: " + matcher.path + "\n");
        } else if (matcher instanceof ExactMatcher) {
          sb.append("- exact: " + matcher.path + "\n");
        } else if (matcher instanceof SuffixMatcher) {
          sb.append("- suffix: " + matcher.path + "\n");
        } else if (matcher instanceof PartialRegExMatcher) {
          sb.append("- partial_regex: " + matcher.path + "\n");
        }
        sb.append("  owners: \n");
        for(Account.Id owner : matcher.getOwners()) {
          sb.append("  - "+mapping.get(owner) + "\n");
        }
      }
    }
    return sb.toString();
  }

  @Override
  void resolvingEmailToAccountIdMocking() throws OrmException {
    mapping.keySet().stream()
      .forEach(id -> {
        try {
          resolveEmail(mapping.get(id),id.get());
        } catch (OrmException e) {
          e.printStackTrace();
        }
      });
  }

  Set<Account.Id> convertEmailsToIds(String[] emails) {
    return Stream.of(emails)
        .map(mapping.inverse()::get)
        .collect(Collectors.toSet());
   }
}
