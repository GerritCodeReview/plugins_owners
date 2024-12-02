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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.entities.RefNames;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

@Singleton
class PathOwnersEntriesCacheImpl implements PathOwnersEntriesCache {

  private final Cache<Key, PathOwnersEntry> cache;
  private final Multimap<String, Key> keysIndex;
  private final LoadingCache<String, Object> keyLocks;

  @Inject
  PathOwnersEntriesCacheImpl(@Named(CACHE_NAME) Cache<Key, PathOwnersEntry> cache) {
    this.cache = cache;
    this.keysIndex = HashMultimap.create();
    this.keyLocks =
        CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10L))
            .build(CacheLoader.from(Object::new));
  }

  @Override
  public PathOwnersEntry get(
      String project, String branch, String path, Callable<PathOwnersEntry> loader)
      throws ExecutionException {
    Key key = new Key(project, branch, path);
    return cache.get(
        key,
        () -> {
          PathOwnersEntry entry = loader.call();
          String indexKey = indexKey(project, branch);
          synchronized (keyLocks.getUnchecked(indexKey)) {
            keysIndex.put(indexKey, key);
          }
          return entry;
        });
  }

  @Override
  public void invalidate(String project, String branch) {
    String indexKey = indexKey(project, branch);
    Collection<Key> keysToInvalidate;

    synchronized (keyLocks.getUnchecked(indexKey)) {
      keysToInvalidate = keysIndex.removeAll(indexKey);
    }

    keysToInvalidate.forEach(cache::invalidate);
  }

  @Override
  public void invalidateIndexKey(Key key) {
    String indexKey = indexKey(key.project, key.branch);

    synchronized (keyLocks.getUnchecked(indexKey)) {
      Collection<Key> values = keysIndex.asMap().get(indexKey);
      if (values != null) {
        values.remove(key);
      }
    }
  }

  private String indexKey(String project, String branch) {
    return new StringBuilder(project).append('@').append(RefNames.fullName(branch)).toString();
  }
}
