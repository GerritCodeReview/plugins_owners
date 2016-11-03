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

## Configuration

Owner approval is determined based on OWNERS files located in the same
repository. They are resolved against the state present in the existing master
branch.

The `OWNERS` files are represented by the following YAML structure:

```yaml
inherited: true
owners:
- user-a@example.com
- user-b@example.com
```

_NOTE: Be aware to double check the emails values to be correctly registered as
Gerrit users, invalid emails are just dropped._

This translates to inheriting owner email address from any parent OWNER files
and to allow user-a@example.com and user-b@example.com to approve the change.

The plugin analyzes the latest patch set by looking at each patch and building
an OWNERS hierarchy. It stops once it finds an OWNERS file that has “inherited”
set to false (by default it’s true.)

For example, imagine the following tree:

```
/OWNERS
/example/src/main/OWNERS
/example/src/main/java/com/example/foo/Foo.java
/example/src/main/resources/config.properties
/example/src/test/OWNERS
/example/src/test/java/com/example/foo/FooTest.java
```

If you submit a patch set that changes /example/src/main/java/com/example/foo/Foo.java
then the plugin will first open /example/src/main/OWNERS and if inherited is set
to true combine it with the owners listed in /OWNERS.

If for each patch there is a reviewer who gave a Code-Review +2 then the plugin
will not add any labels, otherwise it will add label('Owner-Approval', need(_)).

## Example 1

Here’s a sample rules.pl that uses this predicate to enable the submit rule if
one of the owners has given a +2

```prolog
submit_rule(S) :-
  gerrit:default_submit(D),
  D =.. [submit | Ds],
  findall(U, gerrit:commit_label(label('Code-Review', 2), U), Approvers),
  gerrit_owners:add_owner_approval(Approvers, Ds, A),
  S =.. [submit | A].
```
Let's examine how it works:

It first gets the current default on rule which gives ok() if no Code-Review -2
and at least a Code-Review +2 is being provided.

Then it accumulates in Approvers the list of users who had given Code-Review +2
and then checks if this list contains at least one of the owners of this path.

If Approvers list does not contain one of the owners then Owner-Approval need() is added thus
making the change not submittable.

## Example 2

Here's a rule which makes submittable a change if at least one of the owners has
given a +1 without taking into consideration any other label.

```prolog
submit_rule(S) :-
     Ds = [ label(‘owners_plugin_default’,ok(user(100000))) ],
     findall(U, gerrit:commit_label(label('Code-Review', 1), U), Approvers),
     gerrit_owners:add_owner_approval(Approvers, Ds, A),
     S =.. [submit | A].
```


## Example 3

We want to allow an administrator to specify a Code-Review +2 on a change when
all the path owners give consent with a particular new label without requiring
them to have +2 rights on the entire project.

To do that we need (1) to configure in the project a new label and give access
to it to any registered user and then (2) re-use a small variation of the
previous rule.

(1) Let's define in the project general, edit config a new label with values
(label name and values are arbitrary)

```
[label "Owner-Approved"]
     function = NoOp
     defaultValue = 0
     copyMinScore = true
     copyAllScoresOnTrivialRebase = true
     value = -1 I don't want this to be merged
     value =  0 No score
     value = +1 Approved
[access "refs/heads/*"]
     label-Owner-Approved = -1..+1 group Registered Users
```

(2) Use a slightly modified version of the previous rules.pl:

```prolog
submit_rule(S) :-
  gerrit:default_submit(D),
  D =.. [submit | Ds],
  findall(U, gerrit:commit_label(label('Owner-Approved', 1), U), Approvers),
  gerrit_owners:add_owner_approval(Approvers, Ds, A),
  S =.. [submit | A].
```


## Auto assigner

There is a second plugin, gerrit-owners-autoassign which depends on
gerrit-owners. It will automatically assign all of the owners to review a
change when it's created or updated.
