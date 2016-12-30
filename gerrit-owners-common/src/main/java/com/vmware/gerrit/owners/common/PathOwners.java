/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners.common;


import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static com.vmware.gerrit.owners.common.JgitWrapper.getBlobAsBytes;

/**
 * Calculates the owners of a patch list.
 */
// TODO(vspivak): provide assisted factory
public class PathOwners {

  private static final Logger log = LoggerFactory.getLogger(PathOwners.class);

  private final SetMultimap<String, Account.Id> owners;

  private final Repository repository;

  private final PatchList patchList;

  private final ConfigurationParser parser;

  private Map<String, Matcher> matches;

  public PathOwners(AccountResolver resolver, ReviewDb db, Repository repository,
      PatchList patchList) throws OrmException {

    this.repository = repository;
    this.patchList = patchList;
    this.parser = new ConfigurationParser(resolver, db);

    OwnersMap map = fetchOwners();
    owners = Multimaps.unmodifiableSetMultimap(map.getPathOwners());
    matches = map.getMatchers();
  }

  /**
   * Returns a read only view of the paths to owners mapping.
   *
   * @return multimap of paths to owners
   */
  public SetMultimap<String, Account.Id> get() {
    return owners;
  }

  public Map<String,Matcher> getMatches() {
    return matches;
  }

  /**
   * Fetched the owners for the associated patch list.
   *
   * @return A structure containing matchers paths to owners
   */
  private OwnersMap fetchOwners() throws OrmException {
    OwnersMap retMap = new OwnersMap();

    Map<String, PathOwnersEntry> entries = Maps.newHashMap();

    PathOwnersEntry rootEntry = new PathOwnersEntry();
    OwnersConfig rootConfig = getOwnersConfig("","OWNERS");
    if (rootConfig != null) {
      rootEntry.setOwnersPath("OWNERS");
      rootEntry.setOwners(parser.getOwnersFromEmails(rootConfig.getOwners()));
      rootEntry.setMatchers(rootConfig.getMatchers());
    }

    Set<String> paths = getModifiedPaths();
    for (String path : paths) {
      String[] parts = path.split("/");

      PathOwnersEntry currentEntry = rootEntry;
      StringBuilder builder = new StringBuilder();

      // Iterate through the parent paths, not including the file name itself
      for (int i = 0, partsLength = parts.length - 1; i < partsLength; i++) {

        String part = parts[i];

        builder.append(part).append("/");
        String partial = builder.toString();
        log.info("checking partial "+partial);
        // Skip if we already parsed this path
        if (!entries.containsKey(partial)) {
          String ownersPath = partial + "OWNERS";
          OwnersConfig config = getOwnersConfig(partial, ownersPath);
          if (config != null) {
            log.info("Config read is "+config.toString());
            PathOwnersEntry entry = new PathOwnersEntry();
            entry.setOwnersPath(ownersPath);
            entry.setOwners(parser.getOwnersFromEmails(config.getOwners()));
            entry.setMatchers(config.getMatchers());
            log.info("entry is "+entry.toString());

            if (config.isInherited()) {
              entry.getOwners().addAll(currentEntry.getOwners());
            }

            currentEntry = entry;
          }

          entries.put(partial, currentEntry);
        } else {
          currentEntry = entries.get(partial);
        }
      }

      // Only add the path to the OWNERS file to reduce the number of entries in the result
      if (currentEntry.getOwnersPath() != null) {
        retMap.addPathOwners(currentEntry.getOwnersPath(), currentEntry.getOwners());
      }
      log.info("currentEntry "+currentEntry.toString());
      retMap.addMatchers(currentEntry.getMatchers());
    }
    // if any matchers we need to remove matchers not matching any files
    Map<String, Matcher> fullMatchers = retMap.getMatchers();
    if(fullMatchers.size()>0) {
      HashMap<String, Matcher> newMatchers = Maps.newHashMap();
      // extra loop
      for (String path : paths) {
        Iterator<Matcher> it = fullMatchers.values().iterator();
        while(it.hasNext()){
          Matcher matcher = it.next();
          if(matcher.matches(path)){
            newMatchers.put(matcher.getPath(), matcher);
          }

        }

      }
      if(fullMatchers.size() != newMatchers.size()) {
        retMap.setMatchers(newMatchers);
      }
    }

    log.info("retMap has matchers count: "+retMap.getMatchers().size());
    log.info("retMap has owners count: "+retMap.getPathOwners().size());

    return retMap;
  }

  /**
   * Parses the patch list for any paths that were modified.
   *
   * @return set of modified paths.
   */
  private Set<String> getModifiedPaths() {
    Set<String> paths = Sets.newHashSet();
    for (PatchListEntry patch : patchList.getPatches()) {
      // Ignore commit message
      if (!patch.getNewName().equals("/COMMIT_MSG")) {
        paths.add(patch.getNewName());

        // If a file was moved then we need approvals for old and new path
        if (patch.getChangeType() == Patch.ChangeType.RENAMED) {
          paths.add(patch.getOldName());
        }
      }
    }
    return paths;
  }


  /**
   * Returns the parsed FileOwnersConfig file for the given path if it exists.
   *
   * @param partial parent path to be able to do fast indexing
   * @param ownersPath path to OWNERS file in the git repo
   * @return config or null if it doesn't exist
   */
  private OwnersConfig getOwnersConfig(String partial, String ownersPath) {

    try {
      return getBlobAsBytes(repository, "master", ownersPath)
          .flatMap(bytes -> parser.parseYaml(partial, bytes))
          .orElse(null);
    } catch (Exception e) {
      log.warn("Invalid OWNERS file: {}", ownersPath, e);
      return null;
    }
  }



}
