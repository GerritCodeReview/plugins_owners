#!/bin/bash

set -euo pipefail
./$1 --config $2 \
  --dir 'plugins/owners/owners/web/_bazel_ts_out_tests' \
  --test-files 'plugins/owners/owners/web/_bazel_ts_out_tests/*_test.js' \
  --ts-config="plugins/owners/owners/web/tsconfig.json"
