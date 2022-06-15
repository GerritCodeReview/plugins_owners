// Copyright (c) 2013 VMware, Inc. All Rights Reserved.
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
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners.common;

import static com.google.gerrit.reviewdb.client.Patch.COMMIT_MSG;
import static com.google.gerrit.reviewdb.client.Patch.MERGE_LIST;
import static com.googlesource.gerrit.owners.common.JgitWrapper.getBlobAsBytes;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.inject.Inject;
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

/** Calculates the owners of a patch list. */
// TODO(vspivak): provide assisted factory
public class PathOwners {

  private static final Logger log = LoggerFactory.getLogger(PathOwners.class);

  private final SetMultimap<String, Account.Id> owners;

  private final Repository repository;

  private final Repository allprojrepository;

  private final PatchList patchList;

  private final ConfigurationParser parser;

  private final Accounts accounts;

  private Map<String, Matcher> matchers;

  private Map<String, Set<Id>> fileOwners;

  private Map<String, Set<String>> group_fileOwners;

  @Inject private PluginConfigFactory cfg;

  public PathOwners(
      Accounts accounts,
      Repository allprojrepository,
      Repository repository,
      String branch,
      PatchList patchList) {
    this.repository = repository;
    this.allprojrepository = allprojrepository;
    this.patchList = patchList;
    this.parser = new ConfigurationParser(accounts);
    this.accounts = accounts;

    OwnersMap map = fetchOwners(branch);
    owners = Multimaps.unmodifiableSetMultimap(map.getPathOwners());
    matchers = map.getMatchers();
    fileOwners = map.getFileOwners();
    group_fileOwners = map.group_getFileOwners();
  }

  // This is constructor to be called when a particular is configured to disable "owners". So return
  // empty OwnersMap
  public PathOwners(
      Accounts accounts, Repository allprojrepository, Repository repository, PatchList patchList) {
    this.repository = repository;
    this.allprojrepository = allprojrepository;
    this.patchList = patchList;
    this.parser = new ConfigurationParser(accounts);
    this.accounts = accounts;
    OwnersMap map = new OwnersMap();
    owners = Multimaps.unmodifiableSetMultimap(map.getPathOwners());
    matchers = map.getMatchers();
    fileOwners = map.getFileOwners();
    group_fileOwners = map.group_getFileOwners();
  }

  /**
   * Returns a read only view of the paths to owners mapping.
   *
   * @return multimap of paths to owners
   */
  public SetMultimap<String, Account.Id> get() {
    return owners;
  }

  public Map<String, Matcher> getMatchers() {
    return matchers;
  }

  public Map<String, Set<Account.Id>> getFileOwners() {
    return fileOwners;
  }

  public Map<String, Set<String>> group_getFileOwners() {
    return group_fileOwners;
  }

  /**
   * Fetched the owners for the associated patch list.
   *
   * @return A structure containing matchers paths to owners
   */
  private OwnersMap fetchOwners(String branch) {
    OwnersMap ownersMap = new OwnersMap();
    try {
      String rootPath = "OWNERS";

      PathOwnersEntry projectEntry =
          getAllProjOwnersConfig(rootPath, "refs/meta/config")
              .map(
                  conf ->
                      new PathOwnersEntry(
                          rootPath, conf, accounts, Collections.emptySet(), Collections.emptySet()))
              .orElse(new PathOwnersEntry());

      PathOwnersEntry rootProjectEntry =
          getOwnersConfig(rootPath, "refs/meta/config")
              .map(
                  conf ->
                      new PathOwnersEntry(
                          rootPath, conf, accounts, Collections.emptySet(), Collections.emptySet()))
              .orElse(new PathOwnersEntry());

      PathOwnersEntry rootEntry =
          getOwnersConfig(rootPath, branch)
              .map(
                  conf ->
                      new PathOwnersEntry(
                          rootPath, conf, accounts, Collections.emptySet(), Collections.emptySet()))
              .orElse(new PathOwnersEntry());

      Set<String> modifiedPaths = getModifiedPaths();
      Map<String, PathOwnersEntry> entries = new HashMap<>();
      PathOwnersEntry currentEntry = null;
      for (String path : modifiedPaths) {
        currentEntry =
            resolvePathEntry(path, branch, projectEntry, rootProjectEntry, rootEntry, entries);

        // add owners to file for matcher predicates
        ownersMap.group_addFileOwners(path, currentEntry.group_getOwners());

        // Only add the path to the OWNERS file to reduce the number of
        // entries in the result
        if (currentEntry.getOwnersPath() != null) {
          ownersMap.addPathOwners(currentEntry.getOwnersPath(), currentEntry.getOwners());
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

  private void processMatcherPerPath(
      Map<String, Matcher> fullMatchers,
      HashMap<String, Matcher> newMatchers,
      String path,
      OwnersMap ownersMap) {
    Iterator<Matcher> it = fullMatchers.values().iterator();
    boolean flag = false;
    while (it.hasNext()) {
      Matcher matcher = it.next();
      if (!matcher.matches(path) || matcher instanceof GenericMatcher) {
        continue;
      }
      newMatchers.put(matcher.getPath(), matcher);
      ownersMap.addFileOwners(path, matcher.getOwners());
      ownersMap.group_addFileOwners(path, matcher.group_getOwners());
      flag = true;
    }
    if (!flag) {
      Iterator<Matcher> it1 = fullMatchers.values().iterator();
      while (it1.hasNext()) {
        Matcher matcher = (Matcher) it1.next();
        if (matcher.matches(path)
            && matcher instanceof GenericMatcher
            && !matcher.path.equals(".*")) {
          newMatchers.put(matcher.getPath(), matcher);
          ownersMap.addFileOwners(path, matcher.getOwners());
          ownersMap.group_addFileOwners(path, matcher.group_getOwners());
          flag = true;
        }
      }
    }
    if (!flag) {
      Iterator<Matcher> it2 = fullMatchers.values().iterator();
      while (it2.hasNext()) {
        Matcher matcher = (Matcher) it2.next();
        if (matcher.matches(path)
            && matcher instanceof GenericMatcher
            && matcher.path.equals(".*")) {
          newMatchers.put(matcher.getPath(), matcher);
          ownersMap.addFileOwners(path, matcher.getOwners());
          ownersMap.group_addFileOwners(path, matcher.group_getOwners());
        }
      }
    }
  }

  private PathOwnersEntry resolvePathEntry(
      String path,
      String branch,
      PathOwnersEntry projectEntry,
      PathOwnersEntry rootProjectEntry,
      PathOwnersEntry rootEntry,
      Map<String, PathOwnersEntry> entries)
      throws IOException {
    String[] parts = path.split("/");
    PathOwnersEntry currentEntry = rootEntry;
    StringBuilder builder = new StringBuilder();

    if (rootEntry.isInherited()) {
      for (Matcher matcher : rootProjectEntry.getMatchers().values()) {
        if (!currentEntry.hasMatcher(matcher.getPath())) {
          currentEntry.addMatcher(matcher);
        }
      }
      if (currentEntry.getOwners().isEmpty()) {
        currentEntry.setOwners(rootProjectEntry.getOwners());
      }
      if (currentEntry.getOwnersPath() == null) {
        currentEntry.setOwnersPath(projectEntry.getOwnersPath());
      }
    }

    if (rootProjectEntry.isInherited()) {
      for (Matcher matcher : projectEntry.getMatchers().values()) {
        if (!currentEntry.hasMatcher(matcher.getPath())) {
          currentEntry.addMatcher(matcher);
        }
      }
      if (currentEntry.getOwners().isEmpty()) {
        currentEntry.setOwners(rootProjectEntry.getOwners());
      }
      if (currentEntry.getOwnersPath() == null) {
        currentEntry.setOwnersPath(projectEntry.getOwnersPath());
      }
    }

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
        Optional<OwnersConfig> conf = getOwnersConfig(ownersPath, branch);
        final Set<Id> owners = currentEntry.getOwners();
        final Set<String> group_owners = currentEntry.group_getOwners();
        currentEntry =
            conf.map(c -> new PathOwnersEntry(ownersPath, c, accounts, owners, group_owners))
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
      // Ignore commit message and Merge List
      String newName = patch.getNewName();
      if (!COMMIT_MSG.equals(newName) && !MERGE_LIST.equals(newName)) {
        paths.add(newName);

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
  private Optional<OwnersConfig> getOwnersConfig(String ownersPath, String branch)
      throws IOException {
    return getBlobAsBytes(repository, branch, ownersPath)
        .flatMap(bytes -> parser.getOwnersConfig(bytes));
  }

  private Optional<OwnersConfig> getAllProjOwnersConfig(String ownersPath, String branch)
      throws IOException {
    return getBlobAsBytes(allprojrepository, branch, ownersPath)
        .flatMap(bytes -> parser.getOwnersConfig(bytes));
  }
}
