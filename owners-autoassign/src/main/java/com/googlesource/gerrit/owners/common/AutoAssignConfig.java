// Copyright (C) 2021 The Android Open Source Project
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

package com.googlesource.gerrit.owners.common;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.PluginConfigFactory;
import org.eclipse.jgit.lib.Config;

public class AutoAssignConfig {
  public static final String REVIEWERS_SECTION = "reviewers";
  public static final String ASYNC = "async";
  public static final boolean ASYNC_DEF = false;
  public static final String DELAY = "delay";
  public static final long DELAY_MSEC_DEF = 1500L;
  public static final String RETRY_COUNT = "retryCount";
  public static final int RETRY_COUNT_DEF = 2;
  public static final String RETRY_INTERVAL = "retryInterval";
  public static final long RETRY_INTERVAL_MSEC_DEF = DELAY_MSEC_DEF;
  public static final String THREADS = "threads";
  public static final int THREADS_DEF = 1;

  private final boolean asyncReviewers;
  private final int asyncThreads;
  private final int retryCount;
  private final long retryInterval;
  private final long asyncDelay;

  AutoAssignConfig(PluginConfigFactory configFactory, @PluginName String pluginName) {
    Config config = configFactory.getGlobalPluginConfig(pluginName);
    asyncReviewers = config.getBoolean(REVIEWERS_SECTION, ASYNC, ASYNC_DEF);
    asyncThreads = config.getInt(REVIEWERS_SECTION, THREADS, THREADS_DEF);
    asyncDelay =
        ConfigUtil.getTimeUnit(
            config, REVIEWERS_SECTION, null, DELAY, DELAY_MSEC_DEF, MILLISECONDS);

    retryCount = config.getInt(REVIEWERS_SECTION, RETRY_COUNT, RETRY_COUNT_DEF);
    retryInterval =
        ConfigUtil.getTimeUnit(
            config, REVIEWERS_SECTION, null, RETRY_INTERVAL, RETRY_INTERVAL_MSEC_DEF, MILLISECONDS);
  }

  public boolean isAsyncReviewers() {
    return asyncReviewers;
  }

  public int asyncThreads() {
    return asyncThreads;
  }

  public int retryCount() {
    return retryCount;
  }

  public long retryInterval() {
    return retryInterval;
  }

  public long asyncDelay() {
    return asyncDelay;
  }
}
