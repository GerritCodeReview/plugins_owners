/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners;

import com.vmware.gerrit.owners.common.PathOwners;

import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValue;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.patch.PatchList;
import com.google.gwtorm.server.OrmException;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.SystemException;
import org.eclipse.jgit.lib.Repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Branch.NameKey;
import com.google.gerrit.reviewdb.client.Branch;

/**
 * StoredValues for the Gerrit OWNERS plugin.
 */
public class OwnersStoredValues {
  private static final Logger log = LoggerFactory.getLogger(OwnersStoredValues.class);

  public static StoredValue<PathOwners> PATH_OWNERS;

  //private static String branch;

  synchronized
  public static void initialize(final AccountResolver resolver) {
    if (PATH_OWNERS != null) {
      return;
    }
    log.info("Initializing OwnerStoredValues");
    PATH_OWNERS = new StoredValue<PathOwners>() {
      @Override
      protected PathOwners createValue(Prolog engine) {
        PatchList patchList = StoredValues.PATCH_LIST.get(engine);
        Repository repository = StoredValues.REPOSITORY.get(engine);
        Change change = StoredValues.getChange(engine);
        String branch = change.getDest().getShortName();

        PrologEnvironment env = (PrologEnvironment) engine.control;
        
        //get branch name
        /*try {
          branch = repository.getBranch();
          log.info("branch is " + branch);
        } catch (IOException e) {
          log.error("An IOException was caught :"+e.getMessage());
        }*/

        try {
          return new PathOwners(resolver, repository, patchList, branch);
        } catch (OrmException e) {
          throw new SystemException(e.getMessage());
        }
      }
    };
  }

  private OwnersStoredValues() {
  }
}
