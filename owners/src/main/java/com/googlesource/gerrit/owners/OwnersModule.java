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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.rules.PredicateProvider;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;

public class OwnersModule extends AbstractModule {

  private final String noWebLinks;

  @Inject
  OwnersModule(PluginConfigFactory configFactory) {
    Config config = configFactory.getGlobalPluginConfig("owners");
    this.noWebLinks = config.getString("owners", "evo/pvt", "enabled");
  }

  @Override
  protected void configure() {
    DynamicSet.bind(binder(), PredicateProvider.class)
        .to(OwnerPredicateProvider.class)
        .asEagerSingleton();

    install(
        new RestApiModule() {
          protected void configure() {
            get(RevisionResource.REVISION_KIND, "getreview").to(GetReview.class);
            get(RevisionResource.REVISION_KIND, "pendingreview").to(PendingReview.class);
          }
        });
  }
}
