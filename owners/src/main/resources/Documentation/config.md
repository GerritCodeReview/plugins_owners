@PLUGIN@ configuration
======================

## Global configuration

The global plugin configuration is read from the `$GERRIT_SITE/etc/owners.config`
and is applied across all projects in Gerrit.

owners.disable.branch
:   List of branches regex where the resolution of owners is disabled.

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

owners.label
:   Global override for the label and score, separated by a comma, to use by
    the owners of changes for approving them. When defined, it overrides any
    other label definition set by the OWNERS at any level in any project.

    Example:

    ```
    [owners]
      label = Code-Review, 1
    ```

<a name="owners.enableSubmitRequirement">owners.enableSubmitRequirement</a>
:   If set to `true` the approvals are evaluated through the owners plugin
    provided submit rule without a need of prolog predicate being added to a
    project or submit requirement configured in the `project.config` as it is
    automatically applied to all projects. Defaults to `false`.

    Example:

    ```
    [owners]
      enableSubmitRequirement = true
    ```

    > **Notes:**
    >
    > The `owners.enableSubmitRequirement = true` is a global
    > setting and allows for quick site switch from `prolog` submit rule to
    > plugin's provided submit rule. It is a drop-in replacement therefore,
    > similarly to `prolog` rule, it cannot be overridden by Gerrit. In case
    > when one-step migration is not feasible (e.g. when `prolog` rules need to
    > be slowly phased out or when more control is needed over rule's
    > applicability, submitability or ability to overide) one can configure
    > submit requirement in `project.config` for a certain project (or
    > hierarchy of projects), without turning it on globally, as
    > `approval_owners` predicate is _always_ available.
    >
    > Please also note, that project's `rules.pl` should be removed in this
    > case so that it doesn't interfere with a change evaluation.
    >
    > The minimal configuration looks like below (see
    > [submit requirements documentation](/Documentation/config-submit-requirements.html) for more details):
    > ```
    > [submit-requirement "Owner-Approval"]
    >   description = Files needs to be approved by owners
    >   submittableIf = has:approval_owners
    > ```


cache."owners.path_owners_entries".memoryLimit
:   The cache is used to hold the parsed version of `OWNERS` files in the
    repository so that when submit rules are calculated (either through prolog
    or through submit requirements) it is not read over and over again. The
    cache entry gets invalidated when `OWNERS` file branch is updated.
    By default it follows default Gerrit's cache memory limit but it makes
    sense to adjust it as a function of number of project that use the `owners`
    plugin multiplied by average number of active branches (plus 1 for the
    refs/meta/config) and average number of directories (as directory hierarchy
    back to root is checked for the `OWNERS` file existence).
    _Note that in opposite to the previous settings the modification needs to be
    performed in the `$GERRIT_SITE/etc/gerrit.config` file._

    Example

    ```
    [cache "owners.path_owners_entries"]
      memoryLimit = 2048
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

For example, imagine the following tree with a default Gerrit project labels configuration:

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

## Example 1 - OWNERS file without matchers

Given an OWNERS configuration of:

```yaml
inherited: true
owners:
- John Doe
- Doug Smith
```

In this case the owners plugin will assume the default label configuration,`Code-Review
+2`, as indication of approval.

To enforce submittability only when the specified owners have approved the
change, you can then either enable `owners.enableSubmitRequirement = true` in
your `gerrit.config` or define a submit requirement in your `project.config` that
uses the `has:approval_owners` in the `applicableIf` section, like so:
```
[submit-requirement "Owner-Approval"]
       description = Files needs to be approved by owners
       submittableIf = has:approval_owners
```

## Example 2 - OWNERS file with custom approval label

Sometimes, teams might wish to use a different label to signal approval from an
owner, rather than the default `Code-Review +2`. For example, you might wish to
have a governance team explicitly approving changes but do not wish to grant
them code review permissions.

In this case, we need to grant specific groups with a custom label
without necessarily having to give Code-Review +2 rights on the entire project.

Amend the project.config as shown in below and add a new label; then give
permissions to any registered user:

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

Given now an OWNERS configuration of:

```yaml
inherited: true
label: Owner-Approved, 1
owners:
- John Doe
- Doug Smith
```

This will mean that, a change cannot be submitted until 'John Doe' or 'Doug
Smith' vote +1 on the `Owner-Approved`  label, independently from whomever else
has provided a `Code-Review +2`.

Finally to enable this, you'll need to define a custom submit requirement in
your `project.config`(as above) or enable `owners.enableSubmitRequirement = true` in your
`gerrit.config`

_NOTE: If you no longer wish to require a `Code-Review +2` and would rather only
use the custom submit requirement, you will need to change the definition of the
`Code-Review` label in `All-Projects`'s `project.config` so that `function = NoOp`.

> See [notes](#owners.enableSubmitRequirement) for an example on how to enable
> submit requirements for a specific project only.

> See [notes](#label.Label-Name.function) for an example on how to modify
> label functions, as by default `Code-Review` requires at least one max vote
> from any user.
 
## Example 3 - Owners based on matchers

Often the ownership comes from the developer's skills and competencies and
cannot be purely defined by the project's directory structure.
For instance, all the files ending with .sql should be owned and signed-off by
the DBA while all the ones that contain 'Test' should be reviewed by the QA team.

Given an OWNERS configuration of:

```yaml
inherited: true
matchers:
- suffix: .sql
  owners:
  - Mister Dba
- regex: .*Test.*
  owners:
  - John Bug
  - Matt Free
```

You can then either enable `owners.enableSubmitRequirement = true` in your
`gerrit.config` or define a submit requirement in your `project.config` that
uses the `has:approval_owners` in the `applicableIf` section.

> See [notes](#owners.enableSubmitRequirement) for an example on how to enable
> submit requirements for a specific project only.
