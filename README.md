# Gerrit Owners Plugin

The `owners` plugin has the ability to parse the OWNERS file in the repository for
defining who is the owner of the components in a repository and therefore is
entitled to have the final word for the submission of changes associated with
the files owned.

For details on how to configure either plugin, please refer to the docs in the
specific plugin's folder.``

> **NOTE**: A comprehensive introduction to both `owners` and `owners-autoassign` has been
> given as part of the [GerritMeets series in Jan 2025](https://www.youtube.com/watch?v=NfIHnJF30Wo).
> While a further deep dive specifically into newer features of this plugin was
> give at the [GerritMeets in April 2026](https://www.youtube.com/watch?v=DfKYduVKJEY)

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

## Building the plugin

This plugin is built with Bazel using an in Gerrit tree.

Create symbolic links of the owners folders to the Gerrit source code /plugins
directory.

Create a symbolic link of the owners-common-api plugin to the Gerrit source code
directory, which is needed as a pre-requisite for parsing the OWNERS file
and the other utility functions for discovering it inside the repository branches.

>>>>>>> a022e8a (Initial commit)
Then build the owners and owners-autoassign plugins with the usual Gerrit
plugin compile command.

Example:

```
  git clone https://gerrit.googlesource.com/plugins/owners
  git clone https://gerrit.googlesource.com/plugins/owners-common-api
  git clone --recurse-submodules https://gerrit.googlesource.com/gerrit
  cd gerrit/plugins
  ln -s ../../owners .
  ln -s ../../owners-common-api .
  ln -sf owners-common-api/external_plugin_deps.bzl.MODULE.bazel .
  cd ..
  bazel build plugins/owners
```

NOTE: the owners-common folder is producing shared artifacts for the two plugins
and does not need to be built separately being a direct dependency of the build
process. Its resulting .jar must not be installed in gerrit plugins directory.

The output is created in

```
  bazel-bin/plugins/owners/owners.jar
```

To execute the tests run:

```
  bazel test plugins/owners/...
```

This project can be imported into the Eclipse IDE:

Add the plugin name to the `CUSTOM_PLUGINS` (and in case when you want to run
tests from the IDE to `CUSTOM_PLUGINS_TEST_DEPS`) in Gerrit core in
`tools/bzl/plugins.bzl` file and run:

```
  ./tools/eclipse/project.py
```

