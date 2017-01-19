load("//tools/bzl:plugin.bzl", "gerrit_plugin", "PLUGIN_DEPS")
load("//plugins/owners-common:common.bzl", "EXTERNAL_DEPS")

# see common.defs on why this is a java_library2 rather than java_library
java_library(
    name = "owners-common",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = PLUGIN_DEPS + EXTERNAL_DEPS,
)

gerrit_plugin(
    name = "owners_common__plugin",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
)
