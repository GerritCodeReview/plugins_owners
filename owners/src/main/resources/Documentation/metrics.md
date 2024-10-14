Metrics
=============

The following metrics are always emitted:

* plugins/owners/count_configuration_loads
  : the total number of owners configuration loads.

* plugins/owners/load_configuration_latency
  : the latency for loading owners configuration for a change.

When submit requirements are enabled (`owners.enableSubmitRequirement = true`)
these are additionally emitted:

* plugins/owners/count_submit_rule_runs
  : the total number of owners submit rule runs.

* plugins/owners/run_submit_rule_latency
  : the latency for running the owners submit rule.
