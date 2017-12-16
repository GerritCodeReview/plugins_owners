load("//tools:genrule2.bzl", "genrule2")
load("//tools/bzl:plugin.bzl", "gerrit_plugin", "PLUGIN_TEST_DEPS")
load("//owners-common:common.bzl", "EXTERNAL_DEPS")

genrule2(
    name = "all",
    srcs = [
        "//owners",
        "//owners-autoassign",
        "//owners-common",
    ],
    outs = ["all.zip"],
    cmd = " && ".join([
        "cp $(SRCS) $$TMP",
        "cd $$TMP",
        "zip -qr $$ROOT/$@ .",
    ]),
)

java_library(
    name = "owners_classpath_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = EXTERNAL_DEPS + PLUGIN_TEST_DEPS + [
        "//owners:owners__plugin",
        "//owners-autoassign:owners-autoassign__plugin",
        "//owners-common:owners-common__plugin",
    ],
)
