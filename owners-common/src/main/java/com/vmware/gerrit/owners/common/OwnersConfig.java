/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners.common;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * OWNERS file model.
 * <p/>
 * Used for de-serializing the OWNERS files.
 */
public class OwnersConfig {

  @Override
  public String toString() {
    return "OwnersConfig [inherited=" + inherited + ", owners=" + owners
        + ", matchers=" + matchers + "]";
  }

  private static final Logger log = LoggerFactory.getLogger(OwnersConfig.class);

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

  public void addMatcher(String key, Matcher matcher) {
    this.matchers.put(key, matcher);
  }


}
