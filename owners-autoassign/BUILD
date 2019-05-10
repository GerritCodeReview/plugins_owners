load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS", "PLUGIN_DEPS_NEVERLINK", "PLUGIN_TEST_DEPS", "gerrit_plugin")

gerrit_plugin(
    name = "owners-autoassign",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    dir_name = "owners",
    manifest_entries = [
        "Implementation-Title: Gerrit OWNERS autoassign plugin",
        "Implementation-URL: https://gerrit.googlesource.com/plugins/owners",
        "Gerrit-PluginName: owners-autoassign",
        "Gerrit-Module: com.vmware.gerrit.owners.common.AutoassignModule",
        "Gerrit-ApiVersion: 2.15",
    ],
    resources = glob(["src/main/**/*"]),
    deps = [
        "//owners-common",
    ],
)

java_library(
    name = "owners-autoassign_deps",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = PLUGIN_DEPS_NEVERLINK + [
        "//owners-common",
    ],
)

junit_tests(
    name = "owners_autoassign_tests",
    testonly = 1,
    srcs = glob(["src/test/java/**/*.java"]),
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        "//owners-common",
        ":owners-autoassign_deps",
    ],
)
