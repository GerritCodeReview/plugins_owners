// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners.common;

import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public interface PathOwnersEntriesCache {
  static final String CACHE_NAME = "path_owners_entries";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, String.class, PathOwnersEntry.class);
        bind(PathOwnersEntriesCache.class).to(PathOwnersEntriesCacheImpl.class);
        DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
            .to(OwnersRefUpdateListener.class);
      }
    };
  }

  PathOwnersEntry get(String project, String branch, Callable<PathOwnersEntry> loader)
      throws ExecutionException;

  void invalidate(String project, String branch);

  @Singleton
  static class OwnersRefUpdateListener implements GitReferenceUpdatedListener {
    private final PathOwnersEntriesCache cache;

    @Inject
    OwnersRefUpdateListener(PathOwnersEntriesCache cache) {
      this.cache = cache;
    }

    @Override
    public void onGitReferenceUpdated(Event event) {
      cache.invalidate(event.getProjectName(), event.getRefName());
    }
  }
}
