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

import com.google.common.cache.RemovalNotification;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.config.AllUsersName;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public interface PathOwnersEntriesCache {
  String CACHE_NAME = "path_owners_entries";

  static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, Key.class, new TypeLiteral<Optional<OwnersConfig>>() {})
            .maximumWeight(Long.MAX_VALUE)
            .expireAfterWrite(Duration.ofSeconds(60));
        bind(PathOwnersEntriesCache.class).to(PathOwnersEntriesCacheImpl.class);
        DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
            .to(OwnersRefUpdateListener.class);
        DynamicSet.bind(binder(), CacheRemovalListener.class).to(OwnersCacheRemovalListener.class);
      }
    };
  }

  Optional<OwnersConfig> get(
      String project, String branch, String path, Callable<Optional<OwnersConfig>> loader)
      throws ExecutionException;

  void invalidate(String project, String branch);

  void invalidateIndexKey(Key key);

  class Key {
    final String project;
    final String branch;
    final String path;

    Key(String project, String branch, String path) {
      this.project = project;
      this.branch = branch;
      this.path = path;
    }

    @Override
    public int hashCode() {
      return Objects.hash(branch, path, project);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }

      if (obj == null) {
        return false;
      }

      if (getClass() != obj.getClass()) {
        return false;
      }

      Key other = (Key) obj;
      return Objects.equals(branch, other.branch)
          && Objects.equals(path, other.path)
          && Objects.equals(project, other.project);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("Key [project=");
      builder.append(project);
      builder.append(", branch=");
      builder.append(branch);
      builder.append(", path=");
      builder.append(path);
      builder.append("]");
      return builder.toString();
    }
  }

  @Singleton
  class OwnersRefUpdateListener implements GitReferenceUpdatedListener {
    private final PathOwnersEntriesCache cache;
    private final String allUsersName;

    @Inject
    OwnersRefUpdateListener(PathOwnersEntriesCache cache, AllUsersName allUsersName) {
      this.cache = cache;
      this.allUsersName = allUsersName.get();
    }

    @Override
    public void onGitReferenceUpdated(Event event) {
      if (supportedEvent(allUsersName, event)) {
        cache.invalidate(event.getProjectName(), event.getRefName());
      }
    }

    static boolean supportedEvent(String allUsersName, Event event) {
      String refName = event.getRefName();
      return !allUsersName.equals(event.getProjectName())
          && (refName.equals(RefNames.REFS_CONFIG) || !RefNames.isGerritRef(refName));
    }
  }

  @Singleton
  class OwnersCacheRemovalListener implements CacheRemovalListener<Key, PathOwnersEntry> {
    private final PathOwnersEntriesCache cache;
    private final String cacheName;

    @Inject
    OwnersCacheRemovalListener(@PluginName String pluginName, PathOwnersEntriesCache cache) {
      this.cache = cache;
      this.cacheName = String.format("%s.%s", pluginName, CACHE_NAME);
    }

    @Override
    public void onRemoval(
        String pluginName,
        String cacheName,
        RemovalNotification<Key, PathOwnersEntry> notification) {
      if (!this.cacheName.equals(cacheName)) {
        return;
      }

      cache.invalidateIndexKey(notification.getKey());
    }
  }
}
