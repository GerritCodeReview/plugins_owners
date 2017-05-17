load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:plugin.bzl", "gerrit_plugin", "PLUGIN_DEPS", "PLUGIN_TEST_DEPS")
load("//plugins/owners-common:common.bzl", "EXTERNAL_DEPS")

java_library(
    name = "owners-common",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = PLUGIN_DEPS + EXTERNAL_DEPS,
)

gerrit_plugin(
    name = "owners_common__plugin",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
)

junit_tests(
    name = "test",
    testonly = 1,
    srcs = glob(["src/test/java/**/*.java"]),
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":owners-common",
    ],
)
