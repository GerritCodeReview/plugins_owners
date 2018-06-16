workspace(name = "owners")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "7ce9a3f58f030635b3a1229b7fb4c62ee7979bc8",
    #local_path = "/home/<user>/projects/bazlets",
)

# Snapshot Plugin API
load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api_maven_local.bzl",
    "gerrit_api_maven_local",
)

# Load snapshot Plugin API
gerrit_api_maven_local()

# Release Plugin API
#load(
#    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
#    "gerrit_api",
#)

#gerrit_api()

load(":external_plugin_deps_standalone.bzl", "external_plugin_deps_standalone")

external_plugin_deps_standalone()
