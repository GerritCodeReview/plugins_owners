load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS", "PLUGIN_DEPS_NEVERLINK", "PLUGIN_TEST_DEPS", "gerrit_plugin")
load("//lib/prolog:prolog.bzl", "prolog_cafe_library")
load("//owners-common:common.bzl", "EXTERNAL_DEPS", "EXTERNAL_TEST_DEPS")
load("//tools/bzl:junit.bzl", "junit_tests")

PROLOG_PREDICATES = glob(["src/main/java/gerrit_owners/**/*.java"]) + [
    "src/main/java/com/googlesource/gerrit/owners/OwnersMetrics.java",
    "src/main/java/com/googlesource/gerrit/owners/OwnersStoredValues.java",
]

java_library(
    name = "gerrit-owners-predicates",
    srcs = PROLOG_PREDICATES,
    deps = [
        "//owners-common",
        "@prolog-runtime//jar:neverlink",
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
        "Gerrit-Module: com.googlesource.gerrit.owners.OwnersModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        ":gerrit-owners-predicates",
        ":gerrit-owners-prolog-rules",
        "//owners-common",
    ],
)

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
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":owners",
    ],
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":owners",
    ],
)

junit_tests(
    name = "owners_tests",
    srcs = glob(["src/test/java/**/*Test.java"]),
    tags = ["owners"],
    deps = [
        ":owners__plugin_test_deps",
    ],
)

[junit_tests(
    name = f[:f.index(".")].replace("/", "_"),
    srcs = [f],
    tags = ["owners"],
    visibility = ["//visibility:public"],
    deps = [
        ":owners__plugin_test_deps",
    ],
) for f in glob(["src/test/java/**/*IT.java"])]
