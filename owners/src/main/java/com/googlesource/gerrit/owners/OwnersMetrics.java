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

import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
class OwnersMetrics {
  final Counter0 countConfigLoads;
  final Timer0 loadConfig;

  final Counter0 countSubmitRuleRuns;
  final Timer0 runSubmitRule;

  @Inject
  OwnersMetrics(MetricMaker metricMaker) {
    this.countConfigLoads =
        createCounter(
            metricMaker, "count_configuration_loads", "Total number of owners configuration loads");
    this.loadConfig =
        createTimer(
            metricMaker,
            "load_configuration",
            "Latency for loading owners configuration for a change");

    this.countSubmitRuleRuns =
        createCounter(
            metricMaker, "count_submit_rule_runs", "Total number of owners submit rule runs");
    this.runSubmitRule =
        createTimer(metricMaker, "run_submit_rule", "Latency for running the owners submit rule");
  }

  private static Counter0 createCounter(MetricMaker metricMaker, String name, String description) {
    return metricMaker.newCounter(name(name), new Description(description).setRate());
  }

  private static Timer0 createTimer(MetricMaker metricMaker, String name, String description) {
    return metricMaker.newTimer(
        name(name), new Description(description).setCumulative().setUnit(Units.MILLISECONDS));
  }

  private static String name(String name) {
    return String.format("owners/%s", name);
  }
}
