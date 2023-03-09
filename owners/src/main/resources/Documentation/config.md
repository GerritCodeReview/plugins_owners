## Global configuration

The global plugin configuration is read from the `$GERRIT_SITE/etc/owners.config`
and is applied across all projects in Gerrit.

owners.disable.branch
:	List of branches regex where the resolution of owners is disabled.

Example:

```
[owners "disable"]
  branch = refs/meta/config
  branch = refs/heads/sandboxes.*
```

owners.expandGroups
:   Expand owners and groups into account ids. If set to `false` all owners are left untouched, apart from e-mail
    addresses which have the domain dropped. Defaults to `true`.

Example:

```
[owners]
  expandGroups = false
```

owners.enableSubmitRequirement
:   If set to `true` the approvals are evaluated through the owners submit rule without a need of
    prolog predicate being added to a project. Defaults to `false`.

Example:

```
[owners]
  enableSubmitRequirement = true
```

## Configuration

Owner approval is determined based on OWNERS files located in the same
repository on the target branch of the changes uploaded for review.

The `OWNERS` file has the following YAML structure:

```yaml
inherited: true
label: Code-Review, 1
owners:
- some.email@example.com
- User Name
- group/Group of Users
matchers:
- suffix: .java
  owners:
      [...]
- regex: .*/README.*
  owners:
      [...]
- partial_regex: example
  owners:
      [...]
- exact: path/to/file.txt
      [...]
```

_NOTE: Be aware to double check that emails and full user names correspond to
valid registered Gerrit users. When owner user full name or e-mail cannot be
resolved, a corresponding WARN message is logged on Gerrit error_log and the
user entry dropped._

That translates to inheriting owner email address from any parent OWNER files
and to define 'some.email@example.com' or 'User Name' users as the mandatory
reviewers for all changes that include modification to those files.

To specify a group of people instead of naming individual owners, prefix the
group name or UUID with 'group/'.

Additional owners can be specified for files selected by other matching
conditions (matchers section). Matching can be done by file suffix, regex
(partial or full) and exact string comparison. For exact match, path is
relative to the root of the repo.

> **NOTE:** The `generic` matcher is a special type of regex matching that
> is applied only when none of the other sections are matching. It is
> used to define fallback rules. The `generic: .*` is the top-level fallback
> and can be used with other more specific `generic` matchers.

The plugin analyzes the latest patch set by looking at each file directory and
building an OWNERS hierarchy. It stops once it finds an OWNERS file that has
“inherited” set to false (by default it’s true.)

> **NOTE:** The `label` value (default is `Code-Review`) is taken into
> consideration only when `owners.enableSubmitRequirement = true`.
> Owners scores are matched against the label specified in the property in
> question.
> The required label's score can be provided (by default label's scores
> configuration is used) so that owners don't have to be granted with the
> maximum label's score. Note that only single digit (0..9) is allowed.

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
will not add any labels, otherwise, it will add ```label('Code-Review from owners', need(_)).```

## Global project OWNERS

Set a OWNERS file into the project refs/meta/config to define a global set of
rules applied to every change pushed, regardless of the folder or target branch.

Example of assigning every configuration files to a specific owner group:

```yaml
matchers:
- suffix: .config
  owners:
  - Configuration Managers
```

Global refs/meta/config OWNERS configuration is inherited only when the OWNERS file
contain the 'inherited: true' condition at the top of the file or if they are absent.

That means that in the absence of any OWNERS file in the target branch, the refs/meta/config
OWNERS is used as global default.

If the global project OWNERS has the 'inherited: true', it will check for a global project OWNERS
in all parent projects up to All-Projects.

## Example 1 - OWNERS file without matchers and default Gerrit submit rules

Given an OWNERS configuration of:

```yaml
inherited: true
owners:
- John Doe
- Doug Smith
```

### When `owners.enableSubmitRequirement = true`

Then Gerrit would:

Evaluate default submit requirement which gives `OK` if no `Code-Review -2` is
given and at least one `Code-Review +2` is being provided.

Evaluate owners submit requirement to check if `Code-Review +2` is given by
either 'John Doe' or 'Doug Smith'. If none of them has approved then
`Code-Review from owners` requirement is added making the change not
submittable.

### When `owners.enableSubmitRequirement = false` (default)

And sample rules.pl that uses this predicate to enable the submit rule if
one of the owners has given a Code Review +2

```prolog
submit_rule(S) :-
  gerrit:default_submit(D),
  D =.. [submit | Ds],
  findall(U, gerrit:commit_label(label('Code-Review', 2), U), Approvers),
  gerrit_owners:add_owner_approval(Approvers, Ds, A),
  S =.. [submit | A].
```

Then Gerrit would evaluate the Prolog rule as follows:

It first gets the current default on rule which gives ok() if no Code-Review -2
and at least a Code-Review +2 is being provided.

Then it accumulates in Approvers the list of users who had given Code-Review +2
and then checks if this list contains either 'John Doe' or 'Doug Smith'.

If Approvers list does not include one of the owners, then Owner-Approval need()
is added thus making the change not submittable.

## Example 2 - OWNERS file without matchers and no default Gerrit rules

Given an OWNERS configuration of:

```yaml
inherited: true
owners:
- John Doe
- Doug Smith
```

### When `owners.enableSubmitRequirement = true`

This case is supported with the `Code-Review` label and `OWNERS` file
modifications.

The `OWNERS` file requires the label configuration to be added (here is the
updated version):

```yaml
inherited: true
label: Code-Review, 1
owners:
- John Doe
- Doug Smith
```

But additionally one needs to modify the label on the particular project level
to the following version:

```
[label "Code-Review"]
	function = NoOp
	defaultValue = 0
	copyMinScore = true
	copyAllScoresOnTrivialRebase = true
	value = -2 This shall not be merged
	value = -1 I would prefer this is not merged as is
	value = 0 No score
	value = +1 Looks good to me, but someone else must approve
	value = +2 Looks good to me, approved
```

Note that `function` is set to `NoOp`.

As a result Gerrit would make the change Submittable only if 'John Doe' or
'Doug Smith' have provided at least a `Code-Review +1`.

### When `owners.enableSubmitRequirement = false` (default)

And a rule which makes submittable a change if at least one of the owners has
given a +1 without taking into consideration any other label:

```prolog
submit_rule(S) :-
     Ds = [ label(‘owners_plugin_default’,ok(user(100000))) ],
     findall(U, gerrit:commit_label(label('Code-Review', 1), U), Approvers),
     gerrit_owners:add_owner_approval(Approvers, Ds, A),
     S =.. [submit | A].
```

Then Gerrit would make the change Submittable only if 'John Doe' or 'Doug Smith'
have provided at least a Code-Review +1.

## Example 3 - OWNERS file without matchers and custom _Owner-Approves_ label

Sometimes to differentiate the _owners approval_ on a change from the code
review on the entire project. The scenario could be for instance the sign-off of
the project's build dependencies based on the Company roles-and-responsibilities
matrix and governance process.

In this case, we need to grant specific people with the _Owner-Approved_ label
without necessarily having to give Code-Review +2 rights on the entire project.

Amend the project.config as shown in below and add a new label; then give
permissions to any registered user.

Example fo the project config changes with the new label with values
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

### When `owners.enableSubmitRequirement = true`

Given now an OWNERS configuration of:

```yaml
inherited: true
label: Owner-Approved
owners:
- John Doe
- Doug Smith
```

A change cannot be submitted until 'John Doe' or 'Doug Smith' add a label
`Owner-Approved`, independently from being able to provide any Code-Review.

### When `owners.enableSubmitRequirement = false` (default)

Finally, define prolog rules as shown in below (an amended version of
Example 1):

```prolog
submit_rule(S) :-
  gerrit:default_submit(D),
  D =.. [submit | Ds],
  findall(U, gerrit:commit_label(label('Owner-Approved', 1), U), Approvers),
  gerrit_owners:add_owner_approval(Approvers, Ds, A),
  S =.. [submit | A].
```

Given now an OWNERS configuration of:

```yaml
inherited: true
owners:
- John Doe
- Doug Smith
```

A change cannot be submitted until John Doe or Doug Smith add a label
"Owner-Approved", independently from being able to provide any Code-Review.

## Example 4 - OWNERS file without matchers, default Gerrit submit rules and owners Code-Review +1 required

This is a variant of `example 3` when no additional label is created but owners
shouldn't be granted with `Code-Review +2` for all project files as it might be
outside of their comptenence/comfort zone.

### When `owners.enableSubmitRequirement = true`

Given an OWNERS configuration of:

```yaml
inherited: true
label: Code-Review, 1
owners:
- John Doe
- Doug Smith
```

A change cannot be submitted until 'John Doe' or 'Doug Smith' add
`Code-Review+1` score. Note that regular developers still need to cast the
`Code-Review+2` for a change to be submittable as default submit rule is
still evaluated.

## Example 5 - Owners based on matchers

Often the ownership comes from the developer's skills and competencies and
cannot be purely defined by the project's directory structure.
For instance, all the files ending with .sql should be owned and signed-off by
the DBA while all the ones ending with .css by approved by the UX Team.

Given an OWNERS configuration of:

```yaml
inherited: true
matchers:
- suffix: .sql
  owners:
  - Mister Dba
- suffix: .css
  owners:
  - John Creative
  - Matt Designer
```

### When `owners.enableSubmitRequirement = true`

Then for any change that contains files with .sql or .css extensions, besides
to the default Gerrit submit rules, the extra constraints on the additional
owners of the modified files will be added. The final submit is enabled if both
Gerrit default rules are satisfied and all the owners of the .sql files
('Mister Dba') and the .css files (either 'John Creative' or 'Matt Designer')
have provided their `Code-Review +2` feedback (as `Code-Review` is default
label for owners submit requirement).

### When `owners.enableSubmitRequirement = false` (default)

And a rules.pl of:

```prolog
submit_rule(S) :-
  gerrit:default_submit(L),
  L =.. [submit | Sr ],
  gerrit_owners:add_match_owner_approval(Sr,A),
  S =.. [submit | A ].
```

Then for any change that contains files with .sql or .css extensions, besides
to the default Gerrit submit rules, the extra constraints on the additional
owners of the modified files will be added. The final submit is enabled if both
Gerrit default rules are satisfied and all the owners of the .sql files
(Mister Dba) and the .css files (either John Creative or Matt Designer) have
provided their Code-Review +2 feedback.

## Example 6 - Owners details on a per-file basis

### When `owners.enableSubmitRequirement = true`

This case is not yet covered by the owners submit requirement implementation.

### When `owners.enableSubmitRequirement = false` (default)

When using the owners with a series of matchers associated to different set of
owners, it may not be trivial to understand exactly *why* change is not approved
yet.

We need to define one extra submit rule to scan the entire list of files in the
change and their associated owners and cross-check with the existing Code-Review
feedback received.

Given the same OWNERS and rules.pl configuration of Example 4 with the following
extra rule:

```prolog
submit_rule(submit(W)) :-
  gerrit_owners:findall_match_file_user(W).
```

For every change that would include any .sql or .css file (e.g. my-update.sql
and styles.css) Gerrit will display as additional description on the "need" code
review labels section of the change screen:

```
Code-Review from owners
Mister Dba owns my-update.sql
John Creative, Matt Designer own styles.css
```

As soon as the owners reviews are provided, the corresponding entry will be
removed from the "need" section of the change.

In this way, it is always clear which owner needs to provide their feedback on
which file of the change.
