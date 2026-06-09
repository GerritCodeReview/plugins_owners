load("@aspect_rules_ts//ts:defs.bzl", "ts_config", "ts_project")
load("//tools/bzl:js.bzl", "gerrit_js_bundle", "web_test_runner")
load("//tools/js:eslint.bzl", "plugin_eslint")

package_group(
    name = "visibility",
    packages = ["//plugins/owners/..."],
)

package(default_visibility = [":visibility"])

ts_config(
    name = "tsconfig",
    src = "tsconfig.json",
    deps = [
        "//plugins:tsconfig-plugins-base",
    ],
)

ts_project(
    name = "owners-ts",
    srcs = glob(
        ["**/*.ts"],
        exclude = [
            "**/*test*",
            "**/test-utils.ts",
        ],
    ),
    incremental = True,
    out_dir = "_bazel_ts_out",
    tsconfig = ":tsconfig",
    deps = [
        "//plugins:node_modules",
    ],
)

ts_project(
    name = "owners-ts-tests",
    srcs = glob([
        "**/*.ts",
        "**/test-utils.ts",
    ]),
    incremental = True,
    out_dir = "_bazel_ts_out_tests",
    tsconfig = ":tsconfig",
    deps = [
        "//plugins:node_modules",
        "//polygerrit-ui:node_modules",
    ],
)

gerrit_js_bundle(
    name = "gr-owners",
    srcs = [":owners-ts"],
    entry_point = "_bazel_ts_out/plugin.js",
)

web_test_runner(
    name = "web_test_runner",
    srcs = ["web_test_runner.sh"],
    data = [
        ":owners-ts-tests",
        ":tsconfig",
        "//plugins:node_modules",
        "//polygerrit-ui:node_modules",
    ],
)

plugin_eslint()
