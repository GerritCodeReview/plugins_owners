/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners.common;

import static com.vmware.gerrit.owners.common.JgitWrapper.getBlobAsBytes;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;

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

  private final Accounts accounts;

  private Map<String, Matcher> matches;

  private Map<String, Set<Id>> fileOwners;

  public PathOwners(Accounts accounts,
      Repository repository,
      PatchList patchList) {
    this.repository = repository;
    this.patchList = patchList;
    this.parser = new ConfigurationParser(accounts);
    this.accounts = accounts;

    OwnersMap map = fetchOwners();
    owners = Multimaps.unmodifiableSetMultimap(map.getPathOwners());
    matches = map.getMatchers();
    fileOwners = map.getFileOwners();
  }

  /**
   * Returns a read only view of the paths to owners mapping.
   *
   * @return multimap of paths to owners
   */
  public SetMultimap<String, Account.Id> get() {
    return owners;
  }

  public Map<String, Matcher> getMatches() {
    return matches;
  }

  public Map<String, Set<Account.Id>> getFileOwners() {
    return fileOwners;
  }

  /**
   * Fetched the owners for the associated patch list.
   *
   * @return A structure containing matchers paths to owners
   */
  private OwnersMap fetchOwners() {
    OwnersMap ownersMap = new OwnersMap();
    try {
      String rootPath = "OWNERS";
      PathOwnersEntry rootEntry =
          getOwnersConfig(rootPath).map(
              conf -> new PathOwnersEntry(rootPath, conf, accounts, Collections
                  .emptySet())).orElse(new PathOwnersEntry());

      Set<String> modifiedPaths = getModifiedPaths();
      Map<String, PathOwnersEntry> entries = new HashMap<>();
      PathOwnersEntry currentEntry = null;
      for (String path : modifiedPaths) {
        currentEntry =
            resolvePathEntry(path, rootEntry, entries);

        // add owners to file for matcher predicates
        ownersMap.addFileOwners(path,currentEntry.getOwners());

        // Only add the path to the OWNERS file to reduce the number of
        // entries in the result
        if (currentEntry.getOwnersPath() != null) {
          ownersMap.addPathOwners(currentEntry.getOwnersPath(),
              currentEntry.getOwners());
        }
        ownersMap.addMatchers(currentEntry.getMatchers());
      }

      // We need to only keep matchers that match files in the patchset
      Map<String, Matcher> matchers = ownersMap.getMatchers();
      if (matchers.size() > 0) {
        HashMap<String, Matcher> newMatchers = Maps.newHashMap();
        // extra loop
        for (String path : modifiedPaths) {
          processMatcherPerPath(matchers, newMatchers, path, ownersMap);
        }
        if (matchers.size() != newMatchers.size()) {
          ownersMap.setMatchers(newMatchers);
        }
      }
      return ownersMap;
    } catch (IOException e) {
      log.warn("Invalid OWNERS file", e);
      return ownersMap;
    }
  }

  private void processMatcherPerPath(Map<String, Matcher> fullMatchers,
      HashMap<String, Matcher> newMatchers, String path, OwnersMap ownersMap) {
    Iterator<Matcher> it = fullMatchers.values().iterator();
    while (it.hasNext()) {
      Matcher matcher = it.next();
      if (matcher.matches(path)) {
        newMatchers.put(matcher.getPath(), matcher);
        ownersMap.addFileOwners(path, matcher.getOwners());
      }
    }
  }

  private PathOwnersEntry resolvePathEntry(String path,
      PathOwnersEntry rootEntry, Map<String, PathOwnersEntry> entries)
      throws IOException {
    String[] parts = path.split("/");
    PathOwnersEntry currentEntry = rootEntry;
    Set<Id> currentOwners = currentEntry.getOwners();
    StringBuilder builder = new StringBuilder();
    // Iterate through the parent paths, not including the file name
    // itself
    for (int i = 0; i < parts.length - 1; i++) {
      String part = parts[i];
      builder.append(part).append("/");
      String partial = builder.toString();

      // Skip if we already parsed this path
      if (entries.containsKey(partial)) {
        currentEntry = entries.get(partial);
      } else {
        String ownersPath = partial + "OWNERS";
        Optional<OwnersConfig> conf = getOwnersConfig(ownersPath);
        currentEntry =
            conf.map(
                c -> new PathOwnersEntry(ownersPath, c, accounts, currentOwners))
                .orElse(currentEntry);
        if (conf.map(OwnersConfig::isInherited).orElse(false)) {
          for (Matcher m : currentEntry.getMatchers().values()) {
            currentEntry.addMatcher(m);
          }
        }
        entries.put(partial, currentEntry);
      }
    }
    return currentEntry;
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

        // If a file was moved then we need approvals for old and new
        // path
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
   * @param ownersPath path to OWNERS file in the git repo
   * @return config or null if it doesn't exist
   * @throws IOException
   */
  private Optional<OwnersConfig> getOwnersConfig(String ownersPath)
      throws IOException {
    return getBlobAsBytes(repository, "master", ownersPath).flatMap(
        bytes -> parser.getOwnersConfig(bytes));
  }
}
