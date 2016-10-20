/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners;


import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.rules.PredicateProvider;
import com.google.gerrit.server.account.AccountResolver;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Gerrit OWNERS Prolog Predicate Provider.
 */
@Listen
public class OwnerPredicateProvider implements PredicateProvider {
  @Inject
  public OwnerPredicateProvider(Provider<ReviewDb> db, AccountResolver resolver) {
    OwnersStoredValues.initialize(db.get(), resolver);
  }

  @Override
  public ImmutableSet<String> getPackages() {
    return ImmutableSet.of("gerrit_owners");
  }
}
