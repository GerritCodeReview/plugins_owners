load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:plugin.bzl", "gerrit_plugin", "PLUGIN_DEPS", "PLUGIN_TEST_DEPS")
load("//plugins/owners-common:common.bzl", "EXTERNAL_DEPS")

# see common.defs on why this is a java_library2 rather than java_library
java_library(
  name = 'owners-common',
  srcs = glob([
    'src/main/java/**/*.java',
  ]),
  deps = PLUGIN_DEPS + EXTERNAL_DEPS,
  visibility = ["//visibility:public"],
)

gerrit_plugin(
  name = 'owners_common__plugin',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/resources/**/*']),
)

TEST_DEPS = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
':owners-common',
]

junit_tests(
  name = 'test',
  srcs = glob(['src/test/java/**/*Test.java']),
  deps = TEST_DEPS,
)
