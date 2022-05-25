load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS", "PLUGIN_DEPS_NEVERLINK", "PLUGIN_TEST_DEPS", "gerrit_plugin")

gerrit_plugin(
    name = "owners-api",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    dir_name = "owners",
    manifest_entries = [
        "Implementation-Title: Gerrit OWNERS api plugin",
        "Implementation-URL: https://gerrit.googlesource.com/plugins/owners",
        "Gerrit-PluginName: owners-api",
        "Gerrit-Module: com.googlesource.gerrit.owners.api.OwnersApiModule",
    ],
    resources = glob(["src/main/**/*"]),
    deps = [],
)

java_library(
    name = "owners-api_deps",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = PLUGIN_DEPS_NEVERLINK,
)

junit_tests(
    name = "owners_api_tests",
    testonly = 1,
    srcs = glob(["src/test/java/**/*.java"]),
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":owners-api_deps",
    ],
)
