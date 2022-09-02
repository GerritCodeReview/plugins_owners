// Copyright (C) 2022 The Android Open Source Project
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

package com.googlesource.gerrit.owners.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
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
import com.googlesource.gerrit.owners.entities.Owner;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetFilesOwners implements RestReadView<RevisionResource> {
  private static final Logger log = LoggerFactory.getLogger(GetFilesOwners.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final PatchListCache patchListCache;
  private final Accounts accounts;
  private final AccountCache accountCache;
  private final GitRepositoryManager repositoryManager;
  private final PluginSettings pluginSettings;

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
    this.pluginSettings = pluginSettings;
  }

  @Override
  public Response<?> apply(RevisionResource revision)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    PatchSet ps = revision.getPatchSet();
    Change change = revision.getChange();

    try {
      Repository repository = this.repositoryManager.openRepository(change.getProject());
      PatchList patchList = this.patchListCache.get(change, ps);

      String branch = change.getDest().branch();
      PathOwners owners =
          new PathOwners(
              this.accounts,
              repository,
              pluginSettings.isBranchDisabled(branch) ? Optional.empty() : Optional.of(branch),
              patchList);

      Map<String, List<Owner>> fileToOwners =
          owners.getFileOwners().entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      id ->
                          id.getValue().stream()
                              .map(this::getOwnerFromAccountId)
                              .collect(Collectors.toList())));

      return Response.withStatusCode(
          HttpServletResponse.SC_OK, objectMapper.writeValueAsString(fileToOwners));
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

  private Owner getOwnerFromAccountId(Account.Id accountId) {
    return accountCache
        .get(accountId)
        .map(
            accountState -> {
              return new Owner(
                  accountState.account().fullName(), accountState.account().id().toString());
            })
        .orElse(new Owner("Invalid User", "0"));
  }
}
