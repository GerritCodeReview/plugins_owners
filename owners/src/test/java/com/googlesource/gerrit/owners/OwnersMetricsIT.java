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

package com.googlesource.gerrit.owners;

import static com.google.common.truth.Truth.assertWithMessage;

import com.codahale.metrics.MetricRegistry;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GlobalPluginConfig;
import com.google.inject.Inject;
import org.junit.Test;

@TestPlugin(name = "owners", sysModule = "com.googlesource.gerrit.owners.OwnersModule")
@UseLocalDisk
public class OwnersMetricsIT extends LightweightPluginDaemonTest {
  @Inject MetricRegistry metricRegistry;

  @Test
  @GlobalPluginConfig(
      pluginName = "owners",
      name = "owners.enableSubmitRequirement",
      value = "true")
  public void shouldOwnersMetricsBeAvailable() throws Exception {
    // one needs to at least create the OWNERS file to have metrics emitted
    TestAccount admin2 = accountCreator.admin2();
    addOwnerFileToRoot(true, admin2);

    assertMetricExists("plugins/owners/count_configuration_loads");
    assertMetricExists("plugins/owners/load_configuration_latency");
    assertMetricExists("plugins/owners/count_submit_rule_runs");
    assertMetricExists("plugins/owners/run_submit_rule_latency");
  }

  private void assertMetricExists(String name) {
    assertWithMessage(name).that(metricRegistry.getMetrics().get(name)).isNotNull();
  }

  private void addOwnerFileToRoot(boolean inherit, TestAccount u) throws Exception {
    // Add OWNERS file to root:
    //
    // inherited: true
    // owners:
    // - u.email()
    merge(
        createChange(
            testRepo,
            "master",
            "Add OWNER file",
            "OWNERS",
            String.format("inherited: %s\nowners:\n- %s\n", inherit, u.email()),
            ""));
  }
}
