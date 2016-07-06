package com.vmware.gerrit.owners.common;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;

import static java.lang.Integer.MAX_VALUE;
import static org.eclipse.jgit.lib.Constants.CHARACTER_ENCODING;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.FileMode.TYPE_FILE;
import static org.eclipse.jgit.lib.FileMode.TYPE_MASK;

public class BlobUtils {

  /**
   * Get the contents of the blob at the given path in the commit that the
   * revision references.
   *
   * @param repository
   * @param revision
   * @param path
   * @return contents or null if no blob with path at given revision
   */
  public static String getContent(final Repository repository,
                                  final String revision, final String path) {
    return toString(repository, getRawContent(repository, revision, path));
  }

  /**
   * Convert byte array to UTF-8 {@link String}
   *
   * @param repository
   * @param raw
   * @return UTF-8 string or null if bytes are null
   */
  protected static String toString(final Repository repository,
                                   final byte[] raw) {
    if (raw == null)
      return null;
    try {
      return new String(raw, CHARACTER_ENCODING);
    } catch (UnsupportedEncodingException e) {
      throw new GitException(e, repository);
    }
  }

  /**
   * Get raw contents of the blob at the given path in the commit that the
   * revision references.
   *
   * @param repository
   * @param revision
   * @param path
   * @return raw content or null if no blob with path at given revision
   */
  public static byte[] getRawContent(final Repository repository,
                                     final String revision, final String path) {
    if (repository == null)
      throw new IllegalArgumentException("Repository cannot be null");
    if (revision == null)
      throw new IllegalArgumentException("Revision cannot be null");
    if (revision.length() == 0)
      throw new IllegalArgumentException("Revision cannot be empty");
    if (path == null)
      throw new IllegalArgumentException("Path cannot be null");
    if (path.length() == 0)
      throw new IllegalArgumentException("Path cannot be empty");

    final RevCommit commit = parse(repository, strictResolve(repository, revision));
    return getBytes(repository, commit, path);
  }

  /**
   * Parse a commit from the repository
   *
   * @param repository
   * @param commit
   * @return commit
   */
  protected static RevCommit parse(final Repository repository,
                                   final ObjectId commit) {
    final RevWalk walk = new RevWalk(repository);
    walk.setRetainBody(true);
    try {
      return walk.parseCommit(commit);
    } catch (IOException e) {
      throw new GitException(e, repository);
    } finally {
      walk.close();
    }
  }

  /**
   * Resolve the revision string to a commit object id.
   * <p>
   * A {@link GitException} will be thrown when the revision can not be
   * resolved to an {@link ObjectId}
   *
   * @param repository
   * @param revision
   * @return commit id
   */
  protected static ObjectId strictResolve(final Repository repository,
                                          final String revision) {
    final ObjectId resolved = resolve(repository, revision);
    if (resolved == null)
      throw new GitException(MessageFormat.format(
          "Revision ''{0}'' could not be resolved", revision),
                             repository);
    return resolved;
  }

  /**
   * Resolve the revision string to a commit object id
   *
   * @param repository
   * @param revision
   * @return commit id
   */
  protected static ObjectId resolve(final Repository repository,
                                    final String revision) {
    try {
      return repository.resolve(revision);
    } catch (IOException e) {
      throw new GitException(e, repository);
    }
  }

  /**
   * Get the contents of the the blob in the commit located at the given path
   * as a byte array.
   *
   * @param repository
   * @param commit
   * @param path
   * @return raw content
   */
  protected static byte[] getBytes(final Repository repository,
                                   final RevCommit commit, final String path) {
    final ObjectId id = lookupId(repository, commit, path);
    return id != null ? getBytes(repository, id) : null;
  }

  /**
   * Get the id of the blob at the path in the given commit.
   *
   * @param repository
   * @param commit
   * @param path
   * @return blob id, null if not present
   */
  protected static ObjectId lookupId(final Repository repository,
                                     final RevCommit commit, final String path) {
    final TreeWalk walk;
    try {
      walk = TreeWalk.forPath(repository, path, commit.getTree());
    } catch (IOException e) {
      throw new GitException(e, repository);
    }
    if (walk == null)
      return null;
    if ((walk.getRawMode(0) & TYPE_MASK) != TYPE_FILE)
      return null;
    return walk.getObjectId(0);
  }

  /**
   * Get the contents of the the blob with the given id as a byte array.
   *
   * @param repository
   * @param id
   * @return blob bytes
   */
  protected static byte[] getBytes(final Repository repository,
                                   final ObjectId id) {
    try {
      return repository.open(id, OBJ_BLOB).getCachedBytes(MAX_VALUE);
    } catch (IOException e) {
      throw new GitException(e, repository);
    }
  }
}
