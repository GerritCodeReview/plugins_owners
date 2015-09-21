package com.vmware.gerrit.owners.common;

import org.eclipse.jgit.lib.Repository;

public class GitException extends RuntimeException {

  public GitException(Throwable e, Repository repository) {
  }

  public GitException(String format, Repository repository) {
  }
}
