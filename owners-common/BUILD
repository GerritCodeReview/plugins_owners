load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS", "PLUGIN_DEPS_NEVERLINK", "PLUGIN_TEST_DEPS", "gerrit_plugin")
load("//owners-common:common.bzl", "EXTERNAL_DEPS", "EXTERNAL_TEST_DEPS")

java_library(
    name = "owners-common",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = PLUGIN_DEPS_NEVERLINK + EXTERNAL_DEPS,
)

gerrit_plugin(
    name = "owners-common__plugin",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
)

junit_tests(
    name = "test",
    testonly = 1,
    srcs = glob(["src/test/java/**/*.java"]),
    deps = EXTERNAL_TEST_DEPS + PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":owners-common",
    ],
)
