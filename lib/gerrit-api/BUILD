load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "plugin-api",
    visibility = ["//visibility:public"],
    exports = ["@external_plugin_deps//:com_google_gerrit_gerrit_plugin_api"],
)

java_library(
    name = "plugin-api-neverlink",
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = ["@external_plugin_deps//:com_google_gerrit_gerrit_plugin_api"],
)

java_library(
    name = "acceptance-framework",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = ["@external_plugin_deps//:com_google_gerrit_gerrit_acceptance_framework"],
)