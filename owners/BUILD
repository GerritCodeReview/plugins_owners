load("@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl", "gerrit_plugin", "gerrit_plugin_tests")
load("@rules_java//java:defs.bzl", "java_library")
load("//lib/prolog:prolog.bzl", "prolog_cafe_library")
load("//owners-common:common.bzl", "EXT_TEST_DEPS")
load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS_NEVERLINK")

PLUGIN = "owners"

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
    ] + PLUGIN_DEPS_NEVERLINK,
)

java_library(
    name = "prolog-runtime-neverlink",
    neverlink = 1,
    exports = ["@external_deps//:com_googlecode_prolog_cafe_prolog_runtime"],
)

prolog_cafe_library(
    name = "gerrit-owners-prolog-rules",
    srcs = glob(["src/main/prolog/*.pl"]),
    deps = PLUGIN_DEPS_NEVERLINK + [
        ":gerrit-owners-predicates",
    ],
)

gerrit_plugin(
    name = PLUGIN,
    srcs = glob(
        ["src/main/java/**/*.java"],
        exclude = PROLOG_PREDICATES,
    ),
    dir_name = "owners",
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

# Creates the :owners__plugin_test_deps library that the per-file IT loop
# below depends on, plus runs the *Test.java unit tests.
gerrit_plugin_tests(
    srcs = glob(["src/test/java/**/*Test.java"]),
    ext_deps = EXT_TEST_DEPS,
    plugin = PLUGIN,
    tags = ["owners"],
    deps = ["//owners-common"],
)

[junit_tests(
    name = f[:f.index(".")].replace("/", "_"),
    srcs = [f],
    tags = ["owners"],
    visibility = ["//visibility:public"],
    deps = [":owners__plugin_test_deps"],
) for f in glob(["src/test/java/**/*IT.java"])]
