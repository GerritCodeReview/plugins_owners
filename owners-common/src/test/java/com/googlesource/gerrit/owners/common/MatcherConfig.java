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
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners.common;

import org.junit.Ignore;

@Ignore
public class MatcherConfig {
  public static final String MATCH_EXACT = "exact";
  public static final String MATCH_REGEX = "regex";
  public static final String MATCH_SUFFIX = "suffix";
  public static final String MATCH_PARTIAL_REGEX = "partial_regex";
  public static final String MATCH_GENERIC = "generic";

  private final String matchType;
  private final String matchExpr;
  private final String[] owners;

  public static MatcherConfig exactMatcher(String expr, String... owners) {
    return new MatcherConfig(MATCH_EXACT, expr, owners);
  }

  public static MatcherConfig regexMatcher(String expr, String... owners) {
    return new MatcherConfig(MATCH_REGEX, expr, owners);
  }

  public static MatcherConfig suffixMatcher(String expr, String... owners) {
    return new MatcherConfig(MATCH_SUFFIX, expr, owners);
  }

  public static MatcherConfig partialRegexMatcher(String expr, String... owners) {
    return new MatcherConfig(MATCH_PARTIAL_REGEX, expr, owners);
  }

  public static MatcherConfig genericMatcher(String expr, String... owners) {
    return new MatcherConfig(MATCH_GENERIC, expr, owners);
  }

  public MatcherConfig(String matchType, String matchExpr, String[] owners) {
    super();
    this.matchType = matchType;
    this.matchExpr = matchExpr;
    this.owners = owners;
  }

  public String toYaml() {
    StringBuilder sb = new StringBuilder();
    sb.append("- " + matchType + ": " + matchExpr + "\n");
    sb.append("  owners: \n");
    for (String owner : owners) {
      sb.append("  - " + owner + "\n");
    }
    return sb.toString();
  }
}
