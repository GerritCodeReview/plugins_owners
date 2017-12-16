workspace(name = "owners")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "c3edb0d5d53d8fabf37a10010e9a8658316f5f94",
    #local_path = "/home/<user>/projects/bazlets",
)

# Release Plugin API
load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

gerrit_api()

load(":external_plugin_deps_standalone.bzl", "external_plugin_deps_standalone")

external_plugin_deps_standalone()
