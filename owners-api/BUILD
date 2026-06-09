load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_tests",
)

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
)

gerrit_plugin_tests(
    name = "owners_api_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    plugin = "owners-api",
)
