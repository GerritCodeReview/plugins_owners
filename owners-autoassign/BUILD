load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_dependency_tests",
    "gerrit_plugin_tests",
)
load("@rules_java//java:defs.bzl", "java_library")

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
        "Gerrit-Module: com.googlesource.gerrit.owners.common.AutoassignModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        ":owners-api-neverlink",
        "//owners-common",
    ],
)

java_library(
    name = "owners-api-neverlink",
    neverlink = 1,
    exports = ["//plugins/owners-api"],
)

gerrit_plugin_dependency_tests(plugin = "owners-autoassign")

gerrit_plugin_tests(
    name = "owners_autoassign_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    plugin = "owners-autoassign",
    deps = [
        "//owners-common",
        "//plugins/owners-api",
    ],
)
