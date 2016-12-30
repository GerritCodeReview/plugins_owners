package com.vmware.gerrit.owners.common;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.FileMode.TYPE_FILE;
import static org.eclipse.jgit.lib.FileMode.TYPE_MASK;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class JgitWrapper {
  private static final Logger log = LoggerFactory.getLogger(JgitWrapper.class);

  public static Optional<byte[]> getBlobAsBytes(final Repository repository,
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

  private static RevCommit parseCommit(final Repository repository, final ObjectId commit) throws IOException {
    try (final RevWalk walk = new RevWalk(repository)) {
      walk.setRetainBody(true);
      return walk.parseCommit(commit);
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

}
