/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners.common;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

/**
 * OWNERS file model.
 * <p/>
 * Used for de-serializing the OWNERS files.
 */
public class OwnersConfig {
  /**
   * Flag for marking that this OWNERS file inherits from the parent OWNERS.
   */
  private boolean inherited = true;

  /**
   * Set of OWNER email addresses.
   */
  private Set<String> owners = Sets.newHashSet();

  /**
   * Map name of matcher and Matcher (value + Set Owners)
   */
  private Map<String,Matcher> matchers = Maps.newHashMap();

  @Override
  public String toString() {
    return "OwnersConfig [inherited=" + inherited + ", owners=" + owners
        + ", matchers=" + matchers + "]";
  }

  public boolean isInherited() {
    return inherited;
  }

  public void setInherited(boolean inherited) {
    this.inherited = inherited;
  }

  public Set<String> getOwners() {
    return owners;
  }

  public void setOwners(Set<String> owners) {
    this.owners = owners;
  }

  public Map<String,Matcher> getMatchers() {
    return matchers;
  }

  public Matcher addMatcher(Matcher matcher) {
    return this.matchers.put(matcher.path, matcher);
  }
}
