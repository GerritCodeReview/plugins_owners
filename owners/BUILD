load("//tools/bzl:plugin.bzl", "gerrit_plugin", "PLUGIN_DEPS_NEVERLINK")
load("//lib/prolog:prolog.bzl", "prolog_cafe_library")

java_library(
    name = "gerrit-owners-lib",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    deps = [
        "@prolog_runtime//jar:neverlink",
        "//plugins/owners-common:owners-common",
    ] + PLUGIN_DEPS_NEVERLINK,
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
    ],
)
