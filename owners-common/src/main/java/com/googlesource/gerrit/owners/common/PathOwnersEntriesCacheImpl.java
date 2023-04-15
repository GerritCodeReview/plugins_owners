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
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

class PathOwnersEntriesCacheImpl implements PathOwnersEntriesCache {
  private final Cache<String, PathOwnersEntry> cache;

  @Inject
  PathOwnersEntriesCacheImpl(@Named(CACHE_NAME) Cache<String, PathOwnersEntry> cache) {
    this.cache = cache;
  }

  @Override
  public PathOwnersEntry get(String project, String branch, Callable<PathOwnersEntry> loader)
      throws ExecutionException {
    return cache.get(key(project, branch), loader);
  }

  @Override
  public void invalidate(String project, String branch) {
    cache.invalidate(key(project, branch));
  }

  static String key(String project, String branch) {
    return new StringBuilder(project).append('@').append(branch).toString();
  }
}
