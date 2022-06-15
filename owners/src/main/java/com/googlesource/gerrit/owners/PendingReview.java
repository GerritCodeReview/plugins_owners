package com.googlesource.gerrit.owners;

import com.google.common.collect.Sets;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.inject.Inject;
import com.googlesource.gerrit.owners.common.Accounts;
import com.googlesource.gerrit.owners.common.PathOwners;
import com.googlesource.gerrit.owners.common.StreamUtils;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PendingReview extends Object implements RestReadView<RevisionResource> {
  @Inject private PluginConfigFactory cfg;
  private static final Logger log = LoggerFactory.getLogger(PendingReview.class);

  private final PatchListCache patchListCache;

  private final Accounts accounts;

  private final GitRepositoryManager repositoryManager;

  private final String[] disablePatterns;

  @Inject GerritApi gApi;

  @Inject
  PendingReview(
      PatchListCache patchListCache,
      Accounts accounts,
      GitRepositoryManager repositoryManager,
      PluginConfigFactory configFactory) {
    this.patchListCache = patchListCache;
    this.accounts = accounts;
    this.repositoryManager = repositoryManager;
    Config config = configFactory.getGlobalPluginConfig("owners");
    this.disablePatterns = config.getStringList("owners", "disable", "branch");
  }

  @Override
  public Response<String> apply(RevisionResource rev) {
    PatchSet ps = rev.getPatchSet();
    Change change = rev.getChange();

    String string = "";

    int id = rev.getChangeResource().getChange().getChangeId();
    try {
      Repository repository = this.repositoryManager.openRepository(change.getProject());
      Repository allprojrepository =
          this.repositoryManager.openRepository(Project.NameKey.parse("All-Projects"));
      PatchList patchList = this.patchListCache.get(change, ps);

      Set<ListChangesOption> myOptions =
          EnumSet.of(
              ListChangesOption.DETAILED_LABELS,
              ListChangesOption.ALL_REVISIONS,
              ListChangesOption.ALL_COMMITS);
      ChangeInfo ci = gApi.changes().id(id).get(myOptions);

      Map<String, LabelInfo> allLabels = ci.labels;
      List<ApprovalInfo> allApprovals = allLabels.get("Code-Review").all;
      HashSet<Integer> approvedIds = new HashSet<Integer>();
      for (ApprovalInfo eachApproval : allApprovals) {
        if (eachApproval == null || eachApproval.value == null) {
          continue;
        }
        if (eachApproval.value > 0) {
          // log.info("Name: " +  eachApproval.name);
          // log.info("UserName: " +  eachApproval.username);
          // log.info("ID: " + eachApproval._accountId);
          approvedIds.add(eachApproval._accountId);
        }
      }
      String branch = change.getDest().branch();
      PathOwners owners =
          new PathOwners(
              this.accounts, allprojrepository, repository, change.getDest().branch(), patchList);
      for (String pattern : disablePatterns) {
        if (branch.trim().matches(pattern)) {
          owners = new PathOwners(accounts, allprojrepository, repository, patchList);
          break;
        }
      }

      // for (String path : owners.group_getFileOwners().keySet()) {
      //    for (Id each : ownersIds)
      //        log.info("Owner Ids" + each);
      // .map(id -> getFullNameFromId(engine, id)
      // }

      for (String path : owners.group_getFileOwners().keySet()) {

        Set<Integer> ownersIds =
            (Set)
                StreamUtils.iteratorStream(owners.getFileOwners().get(path).iterator())
                    .map(x -> x.get())
                    .collect(Collectors.toSet());
        if (!Sets.intersection(ownersIds, approvedIds).isEmpty()) {
          continue;
        }
        Set<String> ownersNames =
            (Set)
                StreamUtils.iteratorStream(
                        ((Set) owners.group_getFileOwners().get(path)).iterator())
                    .collect(Collectors.toSet());
        Set<String> ownersRefined = new HashSet<String>();
        for (String each : ownersNames) {
          if (each.startsWith("group/")) {
            // ownersRefined.add(each.replace("group/", "")); continue;
            ownersRefined.add(each);
            continue;
          }
          if (each.endsWith("@juniper.net")) {
            ownersRefined.add(each.replace("@juniper.net", ""));
            continue;
          }
          ownersRefined.add(each);
        }

        // String ownVerb = (ownersRefined.size() > 1) ? " own " : " owns ";
        String userNames = (String) ownersRefined.stream().collect(Collectors.joining(", "));
        string = string + userNames + " -> " + (new File(path)).getName() + "\n";
      }

    } catch (NullPointerException error) {
      log.error("NullPointerException, ERROR: " + error.getMessage());
      return Response.withStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, string);
    } catch (PatchListNotAvailableException error) {
      log.error("PatchListNotAvailableException, ERROR: " + error.getMessage());
      return Response.withStatusCode(HttpServletResponse.SC_NOT_FOUND, string);
    } catch (IOException e) {
      log.error("Couldn't open repository: {}", e);
      return Response.withStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, string);
    } catch (Exception error) {
      log.error("Exception, ERROR: " + error.getMessage());
      return Response.withStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, string);
    }
    return Response.ok(string);
  }
}
