load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:plugin.bzl", "gerrit_plugin", "PLUGIN_DEPS", "PLUGIN_TEST_DEPS")
load("//lib/prolog:prolog.bzl", "prolog_cafe_library")
load("//plugins/owners-common:common.bzl", "EXTERNAL_DEPS")

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
        "Gerrit-ApiVersion: 2.14-SNAPSHOT",
    ],
    resources = glob(["src/main/**/*"]),
    deps = [
        "//plugins/owners-common",
    ],
)
