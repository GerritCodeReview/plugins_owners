/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gwt.thirdparty.guava.common.collect.Maps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OWNERS file model.
 * <p/>
 * Used for de-serializing the OWNERS files.
 */
public class OwnersConfig {

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

  public void setMatchers(Map<String, Matcher> matchers) {
    this.matchers = matchers;
  }


}
