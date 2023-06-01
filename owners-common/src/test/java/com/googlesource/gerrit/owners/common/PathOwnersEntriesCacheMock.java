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

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.junit.Ignore;

/** This is a test implementation that doesn't cache anything but calls loader instead. */
@Ignore
public class PathOwnersEntriesCacheMock implements PathOwnersEntriesCache {
  int hit = 0;

  @Override
  public void invalidate(String project, String branch) {}

  @Override
  public void invalidateIndexKey(Key key) {}

  @Override
  public Optional<OwnersConfig> get(
      String project, String branch, String path, Callable<Optional<OwnersConfig>> loader)
      throws ExecutionException {
    try {
      hit++;
      return loader.call();
    } catch (Exception e) {
      throw new ExecutionException(e);
    }
  }
}
