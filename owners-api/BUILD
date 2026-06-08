load("@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl", "gerrit_plugin", "gerrit_plugin_tests")
load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS_NEVERLINK")

PLUGIN = "owners-api"

gerrit_plugin(
    name = PLUGIN,
    srcs = glob(["src/main/java/**/*.java"]),
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
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = PLUGIN_DEPS_NEVERLINK,
)

gerrit_plugin_tests(
    srcs = glob(["src/test/java/**/*.java"]),
    plugin = PLUGIN,
    deps = [":owners-api_deps"],
)
