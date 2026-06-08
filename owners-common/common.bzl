# Maven artifact coordinates for owners-common runtime and test dependencies.
#
# Single source of truth: the "group:artifact" string lists below.
#   * EXT_DEPS / EXT_TEST_DEPS are consumed by bazlets' gerrit_plugin /
#     gerrit_plugin_tests via `ext_deps = ...` in the sibling BUILD files.
#   * EXTERNAL_DEPS / EXTERNAL_TEST_DEPS are derived label lists for plain
#     java_library / junit_tests targets in owners-common/BUILD, using
#     rules_jvm_external's stock artifact() expander.

load("@rules_jvm_external//:defs.bzl", "artifact")

EXT_DEPS = [
    "com.fasterxml.jackson.core:jackson-annotations",
    "com.fasterxml.jackson.core:jackson-core",
    "com.fasterxml.jackson.core:jackson-databind",
    "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml",
    "org.yaml:snakeyaml",
]

EXT_TEST_DEPS = [
    "org.easymock:easymock",
    "org.javassist:javassist",
    "org.powermock:powermock-api-easymock",
    "org.powermock:powermock-api-support",
    "org.powermock:powermock-core",
    "org.powermock:powermock-module-junit4",
    "org.powermock:powermock-module-junit4-common",
    "org.powermock:powermock-reflect",
]

EXTERNAL_DEPS = [artifact(c, repository_name = "owners_plugin_deps") for c in EXT_DEPS]
EXTERNAL_TEST_DEPS = [artifact(c, repository_name = "owners_plugin_deps") for c in EXT_TEST_DEPS]
