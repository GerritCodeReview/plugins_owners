workspace(name = "owners")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "f7b3cc40886f356ee39d5fb6c755109dda0f9536",
    local_path = "/home/<user>/projects/bazlets",
)

# Release Plugin API
load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

gerrit_api()

load(":external_plugin_deps_standalone.bzl", "external_plugin_deps_standalone")

external_plugin_deps_standalone()
