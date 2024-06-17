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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.rules.PredicateProvider;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.googlesource.gerrit.owners.common.PathOwnersEntriesCache;
import com.googlesource.gerrit.owners.common.PluginSettings;

public class OwnersModule extends AbstractModule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PluginSettings pluginSettings;

  @Inject
  OwnersModule(PluginSettings pluginSettings) {
    this.pluginSettings = pluginSettings;
  }

  @Override
  protected void configure() {
    install(PathOwnersEntriesCache.module());
    DynamicSet.bind(binder(), PredicateProvider.class)
        .to(OwnerPredicateProvider.class)
        .asEagerSingleton();
    install(new OwnersRestApiModule());
    install(new OwnersApprovalHasOperand.OwnerApprovalHasOperandModule());

    if (pluginSettings.enableSubmitRequirement()) {
      install(new OwnersSubmitRequirement.OwnersSubmitRequirementModule());
    }
    logger.atInfo().log(
        "Global `owners.enableSubmitRequirement = %b`.", pluginSettings.enableSubmitRequirement());
  }
}
