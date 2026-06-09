load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_api_neverlink",
    "gerrit_plugin_tests",
)
load("@rules_java//java:defs.bzl", "java_library")
load("@rules_jvm_external//:defs.bzl", "artifact")

EXT_REPO = "owners_plugin_deps"

EXT_DEPS = [
    "com.fasterxml.jackson.core:jackson-databind",
    "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml",
]

java_library(
    name = "owners-common",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = gerrit_api_neverlink("owners-common") + [
        artifact(
            c,
            repository_name = EXT_REPO,
        )
        for c in EXT_DEPS
    ],
)

gerrit_plugin_tests(
    name = "test",
    srcs = glob(["src/test/java/**/*.java"]),
    deps = [
        artifact(
            "org.easymock:easymock",
            repository_name = EXT_REPO,
        ),
        ":owners-common",
    ],
)
