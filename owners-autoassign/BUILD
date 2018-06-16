load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "owners-autoassign",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    manifest_entries = [
        "Implementation-Title: Gerrit OWNERS autoassign plugin",
        "Implementation-URL: https://gerrit.googlesource.com/plugins/owners",
        "Gerrit-PluginName: owners-autoassign",
        "Gerrit-Module: com.vmware.gerrit.owners.common.AutoassignModule",
    ],
    resources = glob(["src/main/**/*"]),
    deps = [
        "//owners-common",
    ],
)
