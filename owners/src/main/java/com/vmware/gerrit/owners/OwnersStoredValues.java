/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners;

import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.rules.StoredValue;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.patch.PatchList;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.vmware.gerrit.owners.common.Accounts;
import com.vmware.gerrit.owners.common.PathOwners;


/**
 * StoredValues for the Gerrit OWNERS plugin.
 */
public class OwnersStoredValues {
  private static final Logger log = LoggerFactory.getLogger(OwnersStoredValues.class);

  public static StoredValue<PathOwners> PATH_OWNERS;

  synchronized
  public static void initialize(Accounts accounts) {
    if (PATH_OWNERS != null) {
      return;
    }
    log.info("Initializing OwnerStoredValues");
    PATH_OWNERS = new StoredValue<PathOwners>() {
      @Override
      protected PathOwners createValue(Prolog engine) {
        PatchList patchList = StoredValues.PATCH_LIST.get(engine);
        Repository repository = StoredValues.REPOSITORY.get(engine);
        return new PathOwners(accounts, repository, patchList);
      }
    };
  }

  private OwnersStoredValues() {
  }
}
