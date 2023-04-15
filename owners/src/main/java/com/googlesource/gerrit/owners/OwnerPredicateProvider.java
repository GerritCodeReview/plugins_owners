// Copyright (c) 2013 VMware, Inc. All Rights Reserved.
// Copyright (C) 2017 The Android Open Source Project
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

package com.googlesource.gerrit.owners;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.server.rules.PredicateProvider;
import com.google.inject.Inject;
import com.googlesource.gerrit.owners.common.Accounts;
import com.googlesource.gerrit.owners.common.PathOwnersEntriesCache;
import com.googlesource.gerrit.owners.common.PluginSettings;

/** Gerrit OWNERS Prolog Predicate Provider. */
@Listen
public class OwnerPredicateProvider implements PredicateProvider {
  @Inject
  public OwnerPredicateProvider(
      Accounts accounts,
      PluginSettings config,
      PathOwnersEntriesCache cache,
      OwnersMetrics metrics) {
    OwnersStoredValues.initialize(accounts, config, cache, metrics);
  }

  @Override
  public ImmutableSet<String> getPackages() {
    return ImmutableSet.of("gerrit_owners");
  }
}
