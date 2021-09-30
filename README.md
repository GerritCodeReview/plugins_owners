# Gerrit OWNERS Plugin

This plugin provides some Prolog predicates that can be used to add customized
validation checks based on the approval of ‘path owners’ of a particular folder
in the project.

That allows creating a single big project including multiple components and
users have different roles depending on the particular path where changes are
being proposed. A user can be “owner” in a specific directory, and thus
influencing the approvals of changes there, but cannot do the same in others
paths, so assuring a kind of dynamic subproject access rights.

## How it works

There are currently two main prolog public verbs:

`add_owner_approval/3` (UserList, InList, OutList)
appends `label('Owner-Approval', need(_))` to InList building OutList if
UserList has no users contained in the defined owners of this path change.

In other words, the predicate just copies InList to OutList if at least one of
the elements in UserList is an owner.

`add_owner_approval/2` (InList, OutList)
appends `label('Owner-Approval', need(_))` to InList building OutList if
no owners has given a Code-Review +2  to this path change.

This predicate is similar to the first one but generates a UserList with an
hardcoded policy.

Since add_owner_approval/3 is not using hard coded policies, it can be suitable
for complex customizations.

## Auto assigner

There is a second plugin, gerrit-owners-autoassign which depends on
gerrit-owners. It will automatically assign all of the owners to review a
change when it's created or updated.

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

Add the plugin name to the `CUSTOM_PLUGINS` in Gerrit core in
`tools/bzl/plugins.bzl` file and run:

```
  ./tools/eclipse/project.py
```

