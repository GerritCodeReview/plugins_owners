workspace(name = "owners")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "11ce7521051ca73598d099aa8a396c9ffe932a74",
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
