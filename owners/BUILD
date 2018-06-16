load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:plugin.bzl", "gerrit_plugin", "PLUGIN_DEPS", "PLUGIN_TEST_DEPS", "PLUGIN_DEPS_NEVERLINK")
load("//lib/prolog:prolog.bzl", "prolog_cafe_library")
load("//owners-common:common.bzl", "EXTERNAL_DEPS")

java_library(
    name = "gerrit-owners-lib",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    deps = [
        "@prolog-runtime//jar:neverlink",
        "//owners-common:owners-common",
    ] + PLUGIN_DEPS_NEVERLINK + EXTERNAL_DEPS,
)

prolog_cafe_library(
    name = "gerrit-owners-prolog-rules",
    srcs = glob(["src/main/prolog/*.pl"]),
    deps = PLUGIN_DEPS_NEVERLINK + [
        ":gerrit-owners-lib",
    ],
)

gerrit_plugin(
    name = "owners",
    srcs = ["src/main/java/com/vmware/gerrit/owners/CompileStub.java"],
    manifest_entries = [
        "Implementation-Title: Gerrit OWNERS plugin",
        "Implementation-URL: https://gerrit.googlesource.com/plugins/owners",
        "Gerrit-PluginName: owners",
        "Gerrit-Module: com.vmware.gerrit.owners.OwnersModule",
        "Gerrit-ApiVersion: 2.14-SNAPSHOT",
    ],
    resources = glob(["src/main/**/*"]),
    deps = [
        ":gerrit-owners-lib",
        ":gerrit-owners-prolog-rules",
    ] + EXTERNAL_DEPS,
)
