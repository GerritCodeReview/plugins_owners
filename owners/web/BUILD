load("@npm//@bazel/typescript:index.bzl", "ts_config", "ts_project")
load("//tools/bzl:js.bzl", "gerrit_js_bundle", "karma_test")
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
        "//plugins:tsconfig-plugins-base.json",
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
    tsc = "//tools/node_tools:tsc-bin",
    tsconfig = ":tsconfig",
    deps = [
        "@plugins_npm//@gerritcodereview/typescript-api",
        "@plugins_npm//lit",
        "@plugins_npm//rxjs",
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
    tsc = "//tools/node_tools:tsc-bin",
    tsconfig = ":tsconfig",
    deps = [
        "@plugins_npm//:node_modules",
        "@ui_dev_npm//:node_modules",
    ],
)

gerrit_js_bundle(
    name = "gr-owners",
    srcs = [":owners-ts"],
    entry_point = "_bazel_ts_out/plugin.js",
)

karma_test(
    name = "karma_test",
    srcs = ["karma_test.sh"],
    data = [":owners-ts-tests"],
)

plugin_eslint()
