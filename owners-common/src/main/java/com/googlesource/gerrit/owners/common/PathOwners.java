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

import static com.google.gerrit.entities.Patch.COMMIT_MSG;
import static com.google.gerrit.entities.Patch.MERGE_LIST;
import static com.googlesource.gerrit.owners.common.JgitWrapper.getBlobAsBytes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Account.Id;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffSummary;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Calculates the owners of a patch list. */
// TODO(vspivak): provide assisted factory
public class PathOwners {

  private static final Logger log = LoggerFactory.getLogger(PathOwners.class);

  private enum MatcherLevel {
    Regular,
    Fallback,
    CatchAll;

    static MatcherLevel forMatcher(Matcher matcher) {
      return matcher instanceof GenericMatcher
          ? (matcher.path.equals(".*") ? CatchAll : Fallback)
          : Regular;
    }
  }

  private final SetMultimap<String, Account.Id> owners;

  private final SetMultimap<String, Account.Id> reviewers;

  private final Repository repository;

  private final List<Project.NameKey> parentProjectsNames;

  private final ConfigurationParser parser;

  private final Set<String> modifiedPaths;

  private final Accounts accounts;

  private final GitRepositoryManager repositoryManager;

  private Map<String, Matcher> matchers;

  private Map<String, Set<Id>> fileOwners;

  private Map<String, Set<String>> fileGroupOwners;

  private final boolean expandGroups;

  private final Optional<LabelDefinition> label;

  public PathOwners(
      Accounts accounts,
      GitRepositoryManager repositoryManager,
      Repository repository,
      List<Project.NameKey> parentProjectsNames,
      Optional<String> branchWhenEnabled,
      Map<String, FileDiffOutput> fileDiffMap,
      boolean expandGroups) {
    this(
        accounts,
        repositoryManager,
        repository,
        parentProjectsNames,
        branchWhenEnabled,
        getModifiedPaths(fileDiffMap),
        expandGroups);
  }

  public PathOwners(
      Accounts accounts,
      GitRepositoryManager repositoryManager,
      Repository repository,
      List<Project.NameKey> parentProjectsNames,
      Optional<String> branchWhenEnabled,
      DiffSummary diffSummary,
      boolean expandGroups) {
    this(
        accounts,
        repositoryManager,
        repository,
        parentProjectsNames,
        branchWhenEnabled,
        ImmutableSet.copyOf(diffSummary.getPaths()),
        expandGroups);
  }

  public PathOwners(
      Accounts accounts,
      GitRepositoryManager repositoryManager,
      Repository repository,
      List<Project.NameKey> parentProjectsNames,
      Optional<String> branchWhenEnabled,
      Set<String> modifiedPaths,
      boolean expandGroups) {
    this.repositoryManager = repositoryManager;
    this.repository = repository;
    this.parentProjectsNames = parentProjectsNames;
    this.modifiedPaths = modifiedPaths;
    this.parser = new ConfigurationParser(accounts);
    this.accounts = accounts;
    this.expandGroups = expandGroups;

    OwnersMap map = branchWhenEnabled.map(branch -> fetchOwners(branch)).orElse(new OwnersMap());
    owners = Multimaps.unmodifiableSetMultimap(map.getPathOwners());
    reviewers = Multimaps.unmodifiableSetMultimap(map.getPathReviewers());
    matchers = map.getMatchers();
    fileOwners = map.getFileOwners();
    fileGroupOwners = map.getFileGroupOwners();
    label = map.getLabel();
  }
  /**
   * Returns a read only view of the paths to owners mapping.
   *
   * @return multimap of paths to owners
   */
  public SetMultimap<String, Account.Id> get() {
    return owners;
  }

  /**
   * Returns a read only view of the paths to reviewers mapping.
   *
   * @return multimap of paths to reviewers
   */
  public SetMultimap<String, Account.Id> getReviewers() {
    return reviewers;
  }

  public Map<String, Matcher> getMatchers() {
    return matchers;
  }

  public Map<String, Set<Account.Id>> getFileOwners() {
    return fileOwners;
  }

  public Map<String, Set<String>> getFileGroupOwners() {
    return fileGroupOwners;
  }

  public boolean expandGroups() {
    return expandGroups;
  }

  public Optional<LabelDefinition> getLabel() {
    return label;
  }

  /**
   * Fetched the owners for the associated patch list.
   *
   * @return A structure containing matchers paths to owners
   */
  private OwnersMap fetchOwners(String branch) {
    OwnersMap ownersMap = new OwnersMap();
    try {
      // Using a `map` would have needed a try/catch inside the lamba, resulting in more code
      List<PathOwnersEntry> parentsPathOwnersEntries =
          getPathOwnersEntries(parentProjectsNames, RefNames.REFS_CONFIG);
      PathOwnersEntry projectEntry = getPathOwnersEntry(repository, RefNames.REFS_CONFIG);
      PathOwnersEntry rootEntry = getPathOwnersEntry(repository, branch);

      Map<String, PathOwnersEntry> entries = new HashMap<>();
      PathOwnersEntry currentEntry = null;
      for (String path : modifiedPaths) {
        currentEntry =
            resolvePathEntry(
                path, branch, projectEntry, parentsPathOwnersEntries, rootEntry, entries);

        // add owners and reviewers to file for matcher predicates
        ownersMap.addFileOwners(path, currentEntry.getOwners());
        ownersMap.addFileReviewers(path, currentEntry.getReviewers());
        ownersMap.addFileGroupOwners(path, currentEntry.getGroupOwners());

        // Only add the path to the OWNERS file to reduce the number of
        // entries in the result
        if (currentEntry.getOwnersPath() != null) {
          ownersMap.addPathOwners(currentEntry.getOwnersPath(), currentEntry.getOwners());
          ownersMap.addPathReviewers(currentEntry.getOwnersPath(), currentEntry.getReviewers());
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
      ownersMap.setLabel(Optional.ofNullable(currentEntry).flatMap(PathOwnersEntry::getLabel));
      return ownersMap;
    } catch (IOException e) {
      log.warn("Invalid OWNERS file", e);
      return ownersMap;
    }
  }

  private List<PathOwnersEntry> getPathOwnersEntries(
      List<Project.NameKey> projectNames, String branch) throws IOException {
    ImmutableList.Builder<PathOwnersEntry> pathOwnersEntries = ImmutableList.builder();
    for (Project.NameKey projectName : projectNames) {
      try (Repository repo = repositoryManager.openRepository(projectName)) {
        pathOwnersEntries = pathOwnersEntries.add(getPathOwnersEntry(repo, branch));
      }
    }
    return pathOwnersEntries.build();
  }

  private PathOwnersEntry getPathOwnersEntry(Repository repo, String branch) throws IOException {
    String rootPath = "OWNERS";
    return getOwnersConfig(repo, rootPath, branch)
        .map(
            conf ->
                new PathOwnersEntry(
                    rootPath,
                    conf,
                    accounts,
                    Optional.empty(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet()))
        .orElse(new PathOwnersEntry());
  }

  private void processMatcherPerPath(
      Map<String, Matcher> fullMatchers,
      HashMap<String, Matcher> newMatchers,
      String path,
      OwnersMap ownersMap) {

    Map<MatcherLevel, List<Matcher>> matchersByLevel =
        fullMatchers.values().stream().collect(Collectors.groupingBy(MatcherLevel::forMatcher));
    if (findAndAddMatchers(
        newMatchers, path, ownersMap, matchersByLevel.get(MatcherLevel.Regular))) {
      return;
    }

    if (findAndAddMatchers(
        newMatchers, path, ownersMap, matchersByLevel.get(MatcherLevel.Fallback))) {
      return;
    }

    findAndAddMatchers(newMatchers, path, ownersMap, matchersByLevel.get(MatcherLevel.CatchAll));
  }

  private boolean findAndAddMatchers(
      HashMap<String, Matcher> newMatchers,
      String path,
      OwnersMap ownersMap,
      @Nullable List<Matcher> matchers) {
    if (matchers == null) {
      return false;
    }

    boolean matchingFound = false;

    for (Matcher matcher : matchers) {
      if (matcher.matches(path)) {
        newMatchers.put(matcher.getPath(), matcher);
        ownersMap.addFileOwners(path, matcher.getOwners());
        ownersMap.addFileGroupOwners(path, matcher.getGroupOwners());
        ownersMap.addFileReviewers(path, matcher.getReviewers());
        matchingFound = true;
      }
    }
    return matchingFound;
  }

  private PathOwnersEntry resolvePathEntry(
      String path,
      String branch,
      PathOwnersEntry projectEntry,
      List<PathOwnersEntry> parentsPathOwnersEntries,
      PathOwnersEntry rootEntry,
      Map<String, PathOwnersEntry> entries)
      throws IOException {
    String[] parts = path.split("/");
    PathOwnersEntry currentEntry = rootEntry;
    StringBuilder builder = new StringBuilder();

    // Inherit from Project if OWNER in root enables inheritance
    calculateCurrentEntry(rootEntry, projectEntry, currentEntry);

    // Inherit from Parent Project if OWNER in Project enables inheritance
    for (PathOwnersEntry parentPathOwnersEntry : parentsPathOwnersEntries) {
      calculateCurrentEntry(projectEntry, parentPathOwnersEntry, currentEntry);
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
        Optional<OwnersConfig> conf = getOwnersConfig(repository, ownersPath, branch);
        Optional<LabelDefinition> label = currentEntry.getLabel();
        final Set<Id> owners = currentEntry.getOwners();
        final Set<Id> reviewers = currentEntry.getReviewers();
        Collection<Matcher> inheritedMatchers = currentEntry.getMatchers().values();
        Set<String> groupOwners = currentEntry.getGroupOwners();
        currentEntry =
            conf.map(
                    c ->
                        new PathOwnersEntry(
                            ownersPath,
                            c,
                            accounts,
                            label,
                            owners,
                            reviewers,
                            inheritedMatchers,
                            groupOwners))
                .orElse(currentEntry);
        entries.put(partial, currentEntry);
      }
    }
    return currentEntry;
  }

  private void calculateCurrentEntry(
      PathOwnersEntry rootEntry, PathOwnersEntry projectEntry, PathOwnersEntry currentEntry) {
    if (rootEntry.isInherited()) {
      for (Matcher matcher : projectEntry.getMatchers().values()) {
        if (!currentEntry.hasMatcher(matcher.getPath())) {
          currentEntry.addMatcher(matcher);
        }
      }
      if (currentEntry.getOwners().isEmpty()) {
        currentEntry.setOwners(projectEntry.getOwners());
      }
      if (currentEntry.getOwnersPath() == null) {
        currentEntry.setOwnersPath(projectEntry.getOwnersPath());
      }
      if (currentEntry.getLabel().isEmpty()) {
        currentEntry.setLabel(projectEntry.getLabel());
      }
    }
  }

  /**
   * Parses the diff list for any paths that were modified.
   *
   * @return set of modified paths.
   */
  private static Set<String> getModifiedPaths(Map<String, FileDiffOutput> patchList) {
    Set<String> paths = Sets.newHashSet();
    for (Map.Entry<String, FileDiffOutput> patch : patchList.entrySet()) {
      // Ignore commit message and Merge List
      String newName = patch.getKey();
      if (!COMMIT_MSG.equals(newName) && !MERGE_LIST.equals(newName)) {
        paths.add(newName);

        // If a file was moved then we need approvals for old and new
        // path
        if (patch.getValue().changeType() == Patch.ChangeType.RENAMED) {
          paths.add(patch.getValue().oldPath().get());
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
  private Optional<OwnersConfig> getOwnersConfig(Repository repo, String ownersPath, String branch)
      throws IOException {
    return getBlobAsBytes(repo, branch, ownersPath).flatMap(parser::getOwnersConfig);
  }
}
