load("@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl", "gerrit_plugin", "gerrit_plugin_tests")
load("@rules_java//java:defs.bzl", "java_library")
load("//owners-common:common.bzl", "EXT_TEST_DEPS")
load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS_NEVERLINK")

PLUGIN = "owners-autoassign"

gerrit_plugin(
    name = PLUGIN,
    srcs = glob(["src/main/java/**/*.java"]),
    dir_name = "owners",
    manifest_entries = [
        "Implementation-Title: Gerrit OWNERS autoassign plugin",
        "Implementation-URL: https://gerrit.googlesource.com/plugins/owners",
        "Gerrit-PluginName: owners-autoassign",
        "Gerrit-Module: com.googlesource.gerrit.owners.common.AutoassignModule",
    ],
    resources = glob(["src/main/**/*"]),
    deps = [
        ":owners-api-neverlink",
        "//owners-common",
    ],
)

java_library(
    name = "owners-autoassign_deps",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = PLUGIN_DEPS_NEVERLINK + [
        ":owners-api-neverlink",
        "//owners-common",
    ],
)

java_library(
    name = "owners-api-neverlink",
    neverlink = 1,
    exports = ["//plugins/owners-api"],
)

gerrit_plugin_tests(
    srcs = glob(["src/test/java/**/*.java"]),
    ext_deps = EXT_TEST_DEPS,
    plugin = PLUGIN,
    deps = [
        ":owners-autoassign_deps",
        "//owners-common",
        "//plugins/owners-api",
    ],
)
