#!/bin/bash

set -euo pipefail
./$1 --config $2 \
  --dir 'plugins/owners/web/_bazel_ts_out_tests' \
  --test-files 'plugins/owners/web/_bazel_ts_out_tests/*_test.js' \
  --ts-config="plugins/owners/web/tsconfig.json"
