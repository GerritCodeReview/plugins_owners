# Gerrit OWNERS Plugin

This repository comprosises of effectively two separate plugins, `owners` and
`owners-autoassign`.

They share the ability to parse the same OWNERS file format, which facilitates
the maintenance of ACLs, as there is only one source of truth.

For details on how to configure either plugin, please refer to the docs in the
specific plugin's folder.

> **NOTE**: A comprehensive introduction to both `owners` and `owners-autoassign` has been
> given as part of the [GerritMeets series in Jan 2025](https://www.youtube.com/watch?v=NfIHnJF30Wo).

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

This plugin is built with Bazel and two build modes are supported:

 * Standalone
 * In Gerrit tree

### Build standalone

To build the plugin, issue the following command:

```
  bazel build :all
```

The output is created in

```
  bazel-bin/owners/owners.jar
  bazel-bin/owners-autoassign/owners-autoassign.jar
  bazel-bin/owners-api/owners-api.jar

```

To execute the tests run:

```
  bazel test //...
```

This project can be imported into the Eclipse IDE:

```
  ./tools/eclipse/project.sh
```

## Build in Gerrit tree

Create symbolic links of the owners and owners-autoassign folders and of the
external_plugin_deps.bzl file to the Gerrit source code /plugins directory.

Create a symbolic link of the owners-common plugin to the Gerrit source code
directory.

Then build the owners and owners-autoassign plugins with the usual Gerrit
plugin compile command.

Example:

```
   $ git clone https://gerrit.googlesource.com/plugins/owners
   $ git clone https://gerrit.googlesource.com/gerrit
   $ cd gerrit/plugins
   $ ln -s ../../owners/owners .
   $ ln -s ../../owners/owners-autoassign .
   $ ln -s ../../owners/owners-api .
   $ ln -sf ../../owners/external_plugin_deps.bzl .
   $ cd ..
   $ ln -s ../owners/owners-common .
   $ bazel build plugins/owners plugins/owners-autoassign
```

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

