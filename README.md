# Gerrit OWNERS Plugin

The plugin exposes the `has:approval_owners` predicate that can be used with
Gerrit's own
[submit-requirements](/Documentation/config-submit-requirements.html) to ensure
that a change has been approved by
the relevant users defined in the OWNERS file for the target branch of the
change.

That allows creating a single big project including multiple components and
users with different roles depending on the particular path where changes are
being proposed. A user can be “owner” in a specific directory, and thus
influencing the approvals of changes there, but not in others, enabling great
flexibility when working on repositories shared by multiple teams.

## How it works

Once installed, it will be possible to use the `has:approval_owners` predicate
in Gerrit's own submit requirements. If the plugin is installed but the
predicate isn't used, then the plugin will have no effect.
The plugin will parse the OWNERS file located in the target branch of the change
to compute whose approval is required, only when all the conditions are met will
the change become submittable.

_NOTE: Previous to version 3.6 this plugin supported prolog rules, however these
have been deprecated and their usage is very strongly discouraged._

## Auto-assigner

There is a second plugin, `owners-autoassign` which parses the same OWNERS file.
It will automatically assign all of the owners to review a change when it's
created or updated.
This plugin will require `owners-api.jar` to be in Gerrit's `lib` folder, but
does not depend on `owners.jar` in anyway. The only thing in common between the
two plugin is the fact that they can parse the same OWNERS file format, allowing
users to only define and maintain it once.

## How to build

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

