load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS_NEVERLINK", "gerrit_plugin")
load("//lib/prolog:prolog.bzl", "prolog_cafe_library")

PROLOG_PREDICATES = glob(["src/main/java/gerrit_owners/**/*.java"]) + [
    "src/main/java/com/vmware/gerrit/owners/OwnersStoredValues.java",
]

java_library(
    name = "gerrit-owners-predicates",
    srcs = PROLOG_PREDICATES,
    deps = [
        "@prolog-runtime//jar:neverlink",
        "//owners-common:owners-common",
    ] + PLUGIN_DEPS_NEVERLINK,
)

prolog_cafe_library(
    name = "gerrit-owners-prolog-rules",
    srcs = glob(["src/main/prolog/*.pl"]),
    deps = PLUGIN_DEPS_NEVERLINK + [
        ":gerrit-owners-predicates",
    ],
)

gerrit_plugin(
    name = "owners",
    srcs = glob(
        [
            "src/main/java/**/*.java",
        ],
        exclude = PROLOG_PREDICATES,
    ),
    manifest_entries = [
        "Implementation-Title: Gerrit OWNERS plugin",
        "Implementation-URL: https://gerrit.googlesource.com/plugins/owners",
        "Gerrit-PluginName: owners",
        "Gerrit-Module: com.vmware.gerrit.owners.OwnersModule",
        "Gerrit-ApiVersion: 2.14-SNAPSHOT",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        ":gerrit-owners-predicates",
        ":gerrit-owners-prolog-rules",
        "//owners-common",
    ],
)
