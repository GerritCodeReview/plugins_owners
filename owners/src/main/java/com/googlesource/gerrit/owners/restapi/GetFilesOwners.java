package com.googlesource.gerrit.owners.restapi;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.inject.Inject;
import com.googlesource.gerrit.owners.common.Accounts;
import com.googlesource.gerrit.owners.common.PathOwners;
import com.googlesource.gerrit.owners.common.PluginSettings;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.ObjectUtils;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetFilesOwners implements RestReadView<RevisionResource> {
  private static final Logger log = LoggerFactory.getLogger(GetFilesOwners.class);

  private final PatchListCache patchListCache;
  private final Accounts accounts;
  private final AccountCache accountCache;
  private final GitRepositoryManager repositoryManager;
  private final ImmutableSet<String> disablePatterns;

  @Inject
  GetFilesOwners(
      PatchListCache patchListCache,
      Accounts accounts,
      AccountCache accountCache,
      GitRepositoryManager repositoryManager,
      PluginSettings pluginSettings) {
    this.patchListCache = patchListCache;
    this.accounts = accounts;
    this.accountCache = accountCache;
    this.repositoryManager = repositoryManager;
    this.disablePatterns = pluginSettings.disabledBranchesPatterns();
  }

  @Override
  public Response<?> apply(RevisionResource revision)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    PatchSet ps = revision.getPatchSet();
    Change change = revision.getChange();

    try {
      Repository repository = this.repositoryManager.openRepository(change.getProject());
      Repository allProjectRepository =
          this.repositoryManager.openRepository(Project.NameKey.parse("All-Projects"));
      PatchList patchList = this.patchListCache.get(change, ps);

      String branch = change.getDest().branch();
      PathOwners owners =
          new PathOwners(
              this.accounts,
              allProjectRepository,
              repository,
              change.getDest().branch(),
              patchList);
      for (String pattern : disablePatterns) {
        if (branch.trim().matches(pattern)) {
          owners = new PathOwners(accounts, allProjectRepository, repository, patchList);
          break;
        }
      }

      Map<String, List<String>> fileToOwners =
          owners.getFileOwners().entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      id ->
                          id.getValue().stream()
                              .map(this::getFullNameFromId)
                              .collect(Collectors.toList())));
      return Response.withStatusCode(HttpServletResponse.SC_OK, fileToOwners);
    } catch (PatchListNotAvailableException error) {
      String msg = "Couldn't find patch list: " + error.getMessage();
      log.error(msg);
      return Response.withStatusCode(HttpServletResponse.SC_NOT_FOUND, msg);
    } catch (IOException e) {
      String msg = "Couldn't open repository: " + e.getMessage();
      log.error(msg);
      return Response.withStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg);
    }
  }

  private String getFullNameFromId(Account.Id accountId) {
    return accountCache
        .get(accountId)
        .map(
            accountState ->
                ObjectUtils.firstNonNull(
                    accountState.account().fullName(), accountState.account().id().toString()))
        .orElse("Username Not Available");
  }
}
