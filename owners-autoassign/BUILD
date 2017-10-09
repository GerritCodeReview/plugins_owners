load("//tools/bzl:plugin.bzl", "gerrit_plugin")
load("//plugins/owners/owners-common:common.bzl", "EXTERNAL_DEPS")

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
    deps = [
        "//plugins/owners/owners-common",
    ] + EXTERNAL_DEPS,
    resources = glob(["src/main/resources/**/*"]),
)
