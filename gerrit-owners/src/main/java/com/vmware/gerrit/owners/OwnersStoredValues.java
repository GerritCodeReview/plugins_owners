/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners;

import com.vmware.gerrit.owners.common.PathOwners;

import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValue;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.SystemException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.jgit.lib.ObjectId;

import java.util.List;


/**
 * StoredValues for the Gerrit OWNERS plugin.
 */
public class OwnersStoredValues {
  private static final Logger log = LoggerFactory.getLogger(OwnersStoredValues.class);

  public static StoredValue<PathOwners> PATH_OWNERS;

  synchronized
  public static void initialize(final AccountResolver resolver) {
    if (PATH_OWNERS != null) {
      return;
    }
    log.info("Initializing OwnerStoredValues");
    PATH_OWNERS = new StoredValue<PathOwners>() {
      @Override
      protected PathOwners createValue(Prolog engine) {
        Repository repository = StoredValues.REPOSITORY.get(engine);

        PrologEnvironment env = (PrologEnvironment) engine.control;

        PatchSetInfo psInfo = StoredValues.PATCH_SET_INFO.get(engine);
        PatchListCache plCache = env.getArgs().getPatchListCache();
        List<PatchSetInfo.ParentInfo> parents = psInfo.getParents();
        ChangeData cd = StoredValues.CHANGE_DATA.get(engine);
        Change change;
        try {
          change = cd.change();
        } catch (OrmException e) {
          throw new SystemException("Cannot load change " + cd.getId());
        }
        Project.NameKey projectKey = change.getProject();
        ObjectId a;
        if (parents.isEmpty()) {
          a = null;
        } else {
          a = ObjectId.fromString(parents.get(0).id.get());
        }
        ObjectId b = ObjectId.fromString(psInfo.getRevId());
        Whitespace ws = Whitespace.IGNORE_NONE;
        PatchListKey plKey = new PatchListKey(projectKey, a, b, ws);
        PatchList patchList;
        try {
          patchList = plCache.get(plKey);
        } catch (PatchListNotAvailableException e) {
          throw new SystemException("Cannot create " + plKey);
        }

        try {
          return new PathOwners(resolver, repository, patchList);
        } catch (OrmException e) {
          throw new SystemException(e.getMessage());
        }
      }
    };
  }

  private OwnersStoredValues() {
  }
}
