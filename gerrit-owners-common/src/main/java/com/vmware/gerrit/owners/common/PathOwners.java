/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners.common;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.FileMode.TYPE_FILE;
import static org.eclipse.jgit.lib.FileMode.TYPE_MASK;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * Calculates the owners of a patch list.
 */
// TODO(vspivak): provide assisted factory
public class PathOwners {

  private static final Logger log = LoggerFactory.getLogger(PathOwners.class);

  private final SetMultimap<String, Account.Id> owners;

  private final AccountResolver resolver;

  private final Repository repository;

  private final PatchList patchList;

  private final ReviewDb db;

  public PathOwners(AccountResolver resolver, ReviewDb db, Repository repository, PatchList patchList) throws OrmException {
    this.repository = repository;
    this.resolver = resolver;
    this.patchList = patchList;
    this.db = db;

    owners = Multimaps.unmodifiableSetMultimap(fetchOwners());
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
   * Fetched the owners for the associated patch list.
   *
   * @return multimap of paths to owners
   */
  private SetMultimap<String, Account.Id> fetchOwners() throws OrmException {
    SetMultimap<String, Account.Id> result = HashMultimap.create();
    Map<String, PathOwnersEntry> entries = new HashMap<String, PathOwnersEntry>();

    PathOwnersEntry rootEntry = new PathOwnersEntry();
    OwnersConfig rootConfig = getOwners("OWNERS");
    if (rootConfig != null) {
      rootEntry.setOwnersPath("OWNERS");
      rootEntry.addOwners(getOwnersFromEmails(rootConfig.getOwners()));
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

        // Skip if we already parsed this path
        if (!entries.containsKey(partial)) {
          String ownersPath = partial + "OWNERS";
          OwnersConfig config = getOwners(ownersPath);
          if (config != null) {
            PathOwnersEntry entry = new PathOwnersEntry();
            entry.setOwnersPath(ownersPath);
            entry.addOwners(getOwnersFromEmails(config.getOwners()));

            if (config.isInherited()) {
              entry.addOwners(currentEntry.getOwners());
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
        result.putAll(currentEntry.getOwnersPath(), currentEntry.getOwners());
      }
    }

    return result;
  }

  /**
   * Parses the patch list for any paths that were modified.
   *
   * @return set of modified paths.
   */
  private Set<String> getModifiedPaths() {
    Set<String> paths = new HashSet<String>();
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

  private static RevCommit parseCommit(final Repository repository, final ObjectId commit) throws IOException {
    try (final RevWalk walk = new RevWalk(repository)) {
      walk.setRetainBody(true);
      return walk.parseCommit(commit);
    }
  }

  private static Optional<byte[]> getBlobAsBytes(final Repository repository,
      final String revision, final String path) throws IOException {
    try (final TreeWalk w =
        TreeWalk.forPath(repository, path,
            parseCommit(repository, repository.resolve(revision)).getTree())) {

      return Optional.ofNullable(w)
          .filter(walk -> (walk.getRawMode(0) & TYPE_MASK) == TYPE_FILE)
          .map(walk -> walk.getObjectId(0))
          .flatMap(id -> readBlob(repository, id));
    }
  }

  /**
   * Returns the parsed OwnersConfig file for the given path if it exists.
   *
   * @param ownersPath path to OWNERS file in the git repo
   * @return config or null if it doesn't exist
   */
  private OwnersConfig getOwners(String ownersPath) {

    try {
      return getBlobAsBytes(repository, "master", ownersPath)
          .flatMap(this::parseYaml)
          .orElse(null);
    } catch (Exception e) {
      log.warn("Invalid OWNERS file: {}", ownersPath, e);
      return null;
    }
  }

  private Optional<OwnersConfig> parseYaml(byte[] yamlBytes) {
    try {
      return Optional.of(new ObjectMapper(new YAMLFactory()).readValue(yamlBytes, OwnersConfig.class));
    } catch (IOException e) {
      log.warn("Unable to parse YAML Owners file", e);
      return Optional.empty();
    }
  }

  private static Optional<byte[]> readBlob(Repository repository, ObjectId id) {
      try {
        return Optional.of(repository.open(id, OBJ_BLOB)
            .getCachedBytes(Integer.MAX_VALUE));
      } catch (Exception e) {
        log.error(
            "Unexpected error while reading Git Object " + id, e);
        return Optional.empty();
      }
    }

  /**
   * Translates emails to Account.Ids.
   * @param emails emails to translate
   * @return set of account ids
   */
  private Set<Account.Id> getOwnersFromEmails(Set<String> emails) throws OrmException {
    Set<Account.Id> result = new HashSet<Account.Id>();
    for (String email : emails) {
      Set<Account.Id> ids = resolver.findAll(db, email);
      result.addAll(ids);
    }
    return result;
  }
}
