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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.FileMode.TYPE_FILE;
import static org.eclipse.jgit.lib.FileMode.TYPE_MASK;

import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JgitWrapper {
  private static final Logger log = LoggerFactory.getLogger(JgitWrapper.class);

  public static Optional<byte[]> getBlobAsBytes(Repository repository, String revision, String path)
      throws IOException {
    String refName =
        revision.startsWith(Constants.R_REFS) ? revision : Constants.R_HEADS + revision;
    Ref ref = repository.getRefDatabase().exactRef(refName);
    if (ref == null) {
      return Optional.empty();
    }

    try (final TreeWalk w =
        TreeWalk.forPath(repository, path, parseCommit(repository, ref.getObjectId()).getTree())) {

      return Optional.ofNullable(w)
          .filter(walk -> (walk.getRawMode(0) & TYPE_MASK) == TYPE_FILE)
          .map(walk -> walk.getObjectId(0))
          .flatMap(id -> readBlob(repository, id));
    }
  }

  private static RevCommit parseCommit(Repository repository, ObjectId commit) throws IOException {
    try (final RevWalk walk = new RevWalk(repository)) {
      walk.setRetainBody(true);
      return walk.parseCommit(commit);
    }
  }

  private static Optional<byte[]> readBlob(Repository repository, ObjectId id) {
    try {
      return Optional.of(repository.open(id, OBJ_BLOB).getCachedBytes(Integer.MAX_VALUE));
    } catch (Exception e) {
      // TODO: are we sure we want to swallow this exception?
      log.error("Unexpected error while reading Git Object " + id, e);
      return Optional.empty();
    }
  }
}
