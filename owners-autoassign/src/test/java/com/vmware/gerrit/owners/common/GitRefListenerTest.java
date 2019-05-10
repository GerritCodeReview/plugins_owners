package com.vmware.gerrit.owners.common;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.inject.Inject;
import com.googlesource.gerrit.owners.common.Accounts;
import com.googlesource.gerrit.owners.common.GitRefListener;
import com.googlesource.gerrit.owners.common.ReviewerManager;
import org.eclipse.jgit.lib.Repository;
import org.junit.Ignore;

@Ignore
public class GitRefListenerTest extends GitRefListener {
  int processedEvents = 0;

  @Inject
  public GitRefListenerTest(
      GerritApi api,
      PatchListCache patchListCache,
      GitRepositoryManager repositoryManager,
      Accounts accounts,
      ReviewerManager reviewerManager) {
    super(api, patchListCache, repositoryManager, accounts, reviewerManager);
  }

  @Override
  public void processEvent(Repository repository, Event event) {
    processedEvents++;
  }

  int getProcessedEvents() {
    return processedEvents;
  }
}
