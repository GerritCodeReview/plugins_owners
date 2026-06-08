load("@rules_java//java:defs.bzl", "java_library")
load("//owners-common:common.bzl", "EXTERNAL_DEPS", "EXTERNAL_TEST_DEPS")
load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS", "PLUGIN_DEPS_NEVERLINK", "PLUGIN_TEST_DEPS", "gerrit_plugin")

java_library(
    name = "owners-common",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = PLUGIN_DEPS_NEVERLINK + EXTERNAL_DEPS,
)

junit_tests(
    name = "test",
    testonly = 1,
    srcs = glob(["src/test/java/**/*.java"]),
    jvm_flags = [
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.util.stream=ALL-UNNAMED",
    ],
    deps = EXTERNAL_TEST_DEPS + PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":owners-common",
    ],
)
