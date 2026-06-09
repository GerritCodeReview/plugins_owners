load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_acceptance_framework",
    "gerrit_api",
    "gerrit_api_neverlink",
    "gerrit_plugin",
    "gerrit_plugin_dependency_tests",
    "gerrit_plugin_tests",
)
load("@rules_java//java:defs.bzl", "java_library")
load("//lib/prolog:prolog.bzl", "prolog_cafe_library")

PROLOG_PREDICATES = glob(["src/main/java/gerrit_owners/**/*.java"]) + [
    "src/main/java/com/googlesource/gerrit/owners/OwnersMetrics.java",
    "src/main/java/com/googlesource/gerrit/owners/OwnersStoredValues.java",
]

java_library(
    name = "gerrit-owners-predicates",
    srcs = PROLOG_PREDICATES,
    deps = [
        ":prolog-runtime-neverlink",
        "//owners-common",
    ] + gerrit_api_neverlink("gerrit-owners-predicates"),
)

java_library(
    name = "prolog-runtime-neverlink",
    neverlink = 1,
    exports = ["@external_deps//:com_googlecode_prolog_cafe_prolog_runtime"],
)

prolog_cafe_library(
    name = "gerrit-owners-prolog-rules",
    srcs = glob(["src/main/prolog/*.pl"]),
    deps = gerrit_api_neverlink("gerrit-owners-prolog-rules") + [
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
        "Gerrit-Module: com.googlesource.gerrit.owners.OwnersModule",
        "Gerrit-HttpModule: com.googlesource.gerrit.owners.HttpModule",
    ],
    resource_jars = ["//plugins/owners/web:gr-owners"],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        ":gerrit-owners-predicates",
        ":gerrit-owners-prolog-rules",
        "//owners-common",
    ],
)

gerrit_plugin_dependency_tests(plugin = "owners")

java_library(
    name = "owners__plugin_test_deps",
    testonly = 1,
    srcs = glob(
        ["src/test/java/**/*.java"],
        exclude = [
            "src/test/java/**/*Test.java",
            "src/test/java/**/*IT.java",
        ],
    ),
    visibility = ["//visibility:public"],
    exports = gerrit_api() + gerrit_acceptance_framework() + [
        ":owners",
    ],
    deps = gerrit_api() + gerrit_acceptance_framework() + [
        ":owners",
    ],
)

gerrit_plugin_tests(
    name = "owners_tests",
    srcs = glob(["src/test/java/**/*Test.java"]),
    plugin = "owners",
    deps = [
        ":owners__plugin_test_deps",
    ],
)

[gerrit_plugin_tests(
    name = f[:f.index(".")].replace("/", "_"),
    srcs = [f],
    plugin = "owners",
    visibility = ["//visibility:public"],
    deps = [
        ":owners__plugin_test_deps",
    ],
) for f in glob(["src/test/java/**/*IT.java"])]
