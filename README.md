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

Create three symbolic links of the owners-owners, owners-common and owners-autoassign
from the Gerrit source code /plugins directory to the subdirectories of this project.

Then build the owners and owners-autoassign plugins with the usual Gerrit
plugin compile command.

Example:

```
   $ git clone https://gerrit.googlesource.com/plugins/owners
   $ git clone https://gerrit.googlesource.com/gerrit
   $ cd gerrit/plugins
   $ ln -s ../../owners/owners* .
   $ cd ..
   $ buck build plugins/owners
   $ buck build plugins/owners-autoassign
```

NOTE: the owners-common folder is producing shared artifacts for the two plugins
and does not need to be built separately being a direct dependency of the build
process. Its resulting .jar must not be installed in gerrit plugins directory.
