/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners;


import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.rules.PredicateProvider;
import com.google.inject.Inject;
import com.vmware.gerrit.owners.common.Accounts;

/**
 * Gerrit OWNERS Prolog Predicate Provider.
 */
@Listen
public class OwnerPredicateProvider implements PredicateProvider {
  @Inject
  public OwnerPredicateProvider(Accounts accounts) {
    OwnersStoredValues.initialize(accounts);
  }

  @Override
  public ImmutableSet<String> getPackages() {
    return ImmutableSet.of("gerrit_owners");
  }
}
