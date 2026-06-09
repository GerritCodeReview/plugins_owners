# Gerrit OWNERS Plugin

This repository comprises of effectively two separate plugins, `owners` and
`owners-autoassign`.

They share the ability to parse the same OWNERS file format, which facilitates
the maintenance of ACLs, as there is only one source of truth.

For details on how to configure either plugin, please refer to the docs in the
specific plugin's folder.

> **NOTE**: A comprehensive introduction to both `owners` and `owners-autoassign` has been
> given as part of the [GerritMeets series in Jan 2025](https://www.youtube.com/watch?v=NfIHnJF30Wo).
> While a further deep dive specifically into newer features of this plugin was
> give at the [GerritMeets in April 2026](https://www.youtube.com/watch?v=DfKYduVKJEY)

Here's an introduction to both plugins:

## owners

This plugin exposes the `has:approval_owners` predicate that can be used with
Gerrit's own
[submit-requirements](/Documentation/config-submit-requirements.html) to ensure
that a change has been approved by
the relevant users defined in the OWNERS file for the target branch of the
change.

This allows the creation of a single repository with multiple nested projects, each
potentially, used by different users/teams with different roles depending on the
particular path where changes are being proposed. A user can be “owner” in a
specific directory, thus influencing the approvals of changes there, but not
in others, enabling great flexibility when working on repositories shared by
multiple teams.

## owners-autoassign

This plugin parses the same OWNERS file format as the owners plugin. It will
automatically assign all of the owners as reviewers to newly created or updated
changes. It also allows for completely custom management of the attention set,
i.e. allows, via custom integrations, to not add people on holiday to the
attention set, or that the same user is not added to too many changes at the
same time, etc...

## Building the plugins

The plugins are built with Bazel as part of the Gerrit source tree.

The owners repo is its own Bazel module (`gerrit-owners`). It is wired
into Gerrit's bzlmod graph by a single symlink to the owners repo root
under `gerrit/plugins/`, plus the `external_plugin_deps.MODULE.bazel`
fragment that includes it.

Example:

```
  git clone https://gerrit.googlesource.com/plugins/owners
  git clone --recurse-submodules https://gerrit.googlesource.com/gerrit
  cd gerrit/plugins
  ln -sn ../../owners owners
  ln -sf ../../owners/external_plugin_deps.MODULE.bazel .
  cd ..
  bazelisk build plugins/owners plugins/owners-api plugins/owners-autoassign
```

That single `plugins/owners` symlink also reaches `owners-common`,
`owners-api`, and `owners-autoassign` (they're sibling subdirectories
inside the owners repo). No separate symlinks under `gerrit/plugins/`
for those, and no `gerrit/owners-common` symlink at the gerrit root.

To re-pin Maven dependencies after editing `MODULE.bazel`, run REPIN
from the owners checkout (the `@owners_plugin_deps` repository is
declared in `owners/MODULE.bazel`):

```
  cd owners
  REPIN=1 bazelisk run @owners_plugin_deps//:pin
```

This writes back to `owners/owners_plugin_deps.lock.json` (at the
owners repo root). Commit the updated lock file alongside the
`MODULE.bazel` change.

NOTE: the owners-common folder is producing shared artifacts for the two plugins
and does not need to be built separately being a direct dependency of the build
process. Its resulting .jar must not be installed in gerrit plugins directory.

The output is created in

```
  bazel-bin/plugins/owners/owners.jar
  bazel-bin/plugins/owners-autoassign/owners-autoassign.jar
```

To execute the tests run:

```
  bazel test owners-common:test
```

This project can be imported into the Eclipse IDE:

Add the plugin name to the `CUSTOM_PLUGINS` (and in case when you want to run
tests from the IDE to `CUSTOM_PLUGINS_TEST_DEPS`) in Gerrit core in
`tools/bzl/plugins.bzl` file and run:

```
  ./tools/eclipse/project.py
```

