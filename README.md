# Gerrit OWNERS Plugin

This plugin provides some Prolog predicates that can be used to add customized validation checks based on the approval
 of 'path owners' of a specific folder in the project.

 This allows to create a single big project having "subprojects" inside where users can have different roles depending
 on the specific path where changes are being proposed. A user can be "owner" in a specific path, and thus
  influencing the approvals of changes there, but can't do the same in others paths, so assuring a kind of dynamic
  subproject access rights.

 There are currently two main prolog public verbs:

 `add_owner_approval/3` (Users, In, Out) appends `label('Owner-Approval', need(_))` to In list building Out if
 Users does not contain ALL the defined owners of this path change.

  `add_owner_approval/2` (In, Out) appends `label('Owner-Approval', need(_))` to In building Out if
  all the path owners have not given a Code-Review +2  of this path change.

Note that the `add_owner_approval/3` is not using hardcoded labels so it can be more suitable for complex customizations.

Owner approval is determined based on OWNERS files located in the same repository. They are resolved against the state present in the existing master branch.

The OWNERS files are represented by the following YAML structure:

```yaml
inherited: true
owners:
- user-a@example.com
- user-b@example.com
```
NOTE: Be aware to double check the emails values to be correctly registered as Gerrit users, invalid emails are simply dropped.

This translates to inheriting owner email address from any parent OWNER files and to allow `user-a@example.com` and `user-b@example.com` to approve the change.

The plugin analyzes the latest patch set by looking at each patch and building an OWNERS hierarchy. It stops once it finds an OWNERS file that has “inherited” set to false (by default it’s true.)

For example, imagine the following tree:

```
/OWNERS
/example/src/main/OWNERS
/example/src/main/java/com/example/foo/Foo.java
/example/src/main/resources/config.properties
/example/src/test/OWNERS
/example/src/test/java/com/example/foo/FooTest.java
```

If you submit a patch set that changes `/example/src/main/java/com/example/foo/Foo.java` then the plugin will first open `/example/src/main/OWNERS` and if inherited is set to true combine it with the owners listed in `/OWNERS`.

If for each patch there is a reviewer who gave a `Code-Review +2` then the plugin will not add any labels,
otherwise it will add `label('Owner-Approval', need(_))`.

Example 1:

Here’s a sample rules.pl that uses this predicate to enable the submit rule if all the owners have given a +2

```prolog
submit_rule(S) :-
  gerrit:default_submit(D),
  D =.. [submit | Ds],
  findall(U, gerrit:commit_label(label('Code-Review', 2), U), Approvers),
  gerrit_owners:add_owner_approval(Approvers, Ds, A),
  S =.. [submit | A].
```
Let's examine how it works:

It first gets the current default submit rule which gives ok() if no Code-Review -2 and at least a Code-Review +2 is being provided.

 Then we accumulate in Approvers the list of users who had given Code-Review +2 and then check if this list contains all the owners of this path.

 If all the owners are not in that list a Owner-Approval need() is added thus making the change not submittable.

Example 2:

Here's a rule which makes submittable a change if all the owners have given a +1 without taking in consideration any other label.

```submit_rule(S) :-
     Ds = [ label(‘owners_plugin_default’,ok(user(100000))) ],
     findall(U, gerrit:commit_label(label('Code-Review', 1), U), Approvers),
     gerrit_owners:add_owner_approval(Approvers, Ds, A),
     S =.. [submit | A].
```


Example 3:

 We want to allow an administrator to specify a Code-Review +2 on a change when all the path owners give consent with a specific new label
  without requiring them to have +2 rights on the entire project.

To do that we need (1) to configure in the project a new label, (2) give access to it to any registered user and then
(3) re-use a small variation of the previous rule.

 (1) Let's define in the project general, edit config a new label with values (name and values of the label are arbitrary)

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
(2) Allow Registered Users to use the label in the main project access

```
Reference:
refs/heads/*
Label Owner-Approved Exclusive
   Registered Users
```

(3) Use a slightly modified version of the previous rules.pl:

```prolog
submit_rule(S) :-
  gerrit:default_submit(D),
  D =.. [submit | Ds],
  findall(U, gerrit:commit_label(label('Owner-Approved', 1), U), Approvers),
  gerrit_owners:add_owner_approval(Approvers, Ds, A),
  S =.. [submit | A].
```


## Auto assigner

There is a second plugin, `gerrit-owners-autoassign` which depends on `gerrit-owners`. It will automatically assign
all of the owners to review a change when it's created or updated.
