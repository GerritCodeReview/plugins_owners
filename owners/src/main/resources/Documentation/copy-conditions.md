@PLUGIN@ CopyCondition
======================

The @PLUGIN@ plugin provides an
additional [copy condition](https://gerrit-review.googlesource.com/Documentation/config-labels.html#label_copyCondition)
operand that can be used in label definitions to control when owner approvals should be preserved
across patch sets.

This document describes the behavior of the operand, its purpose, and how to
configure it.

## approverin:already-approved-by_owners

This operand enables approval copying based on file ownership and patch-set differences.
It allows owner approvals to be copied over if the owned files in the latest patch set haven't been
modified.

By doing so, it reduces unnecessary re-review, allowing owners to retain their previous approval
when their files are unchanged.

When evaluating a label's `copyCondition`, the operand `approverin:already-approved-by_owners`
returns true only when:

1. The approval was previously given by a user who owns at least one file in the change; and
2. The newly uploaded patch set does not modify any files owned by that user (edits due to
   rebase-only are ignored).

If a patch set updates files that the approver owns, the approval is cleared and the owner is
expected to review the new changes. However, if the modifications are identified as rebase edits,
they are treated as unchanged, and the approval is preserved.

**NOTE**: this operand is only supported for `approverin:` predicates. Using it with an `uploaderin`
predicate will result in the label **not** being copied (and an error emitted in the logs).

## Example Scenarios

Given an `OWNERS` file that splits ownerships between `frontend` users and `backend` users based on
file matchers:

```text
inherited: true
matchers:
- suffix: .js
  owners:
  - user-frontend
- suffix: .java
  owners:
  - user-backend
```

### Approval copied

* A `user-frontend` owner gives `Code-Review +2` on `Patch Set 1`.
* `Patch Set 2` updates only backend files.
* The `user-frontend` owner does not own any of the modified files in the new Patch Set.

**Result**: The `Code-Review +2` is copied forward.

### Approval copied (edits due to rebase-only)

* A `user-frontend` owner gives `Code-Review +2` on `Patch Set 1`.
* The uploader rebases the change on a different parent (e.g. a new `master` tip) that also brings
  changes to `.js` files.
* `Patch Set 2` is created. `.js` files in `Patch Set 2` are different from `Patch Set 1` (they now
  include the changes from the new parent).

**Result**: The `Code-Review +2` is copied forward.

This is because the `approverin:already-approved-by_owners` predicate ignores changes
that are marked as `due_to_rebase`. Even though the owned files in `Patch Set 2` are
different from `Patch Set 1` (due to the new parent), the content modifications are
determined to be purely from the rebase, not from new edits by the uploader.

### Approval copied (edits due to rebase-only + unrelated file modification)

* A `user-backend` owner gives `Code-Review +2` on `Patch Set 1`.
* The uploader rebases the change as in the previous example (modifying `backend.java` due to
  rebase).
* The uploader *also* includes a change to a file NOT owned by `user-backend`, in the same new Patch
  Set.

**Result**: The `Code-Review +2` is copied forward.

The modification to `backend.java` is ignored because it is exclusively "due to rebase".
The modification to `unrelated.txt` is ignored because `user-backend` does not own it.

### Note on conflict resolutions during rebase

When a rebase encounters conflicts, the resulting patch set necessarily
introduces new content edits by the uploader while resolving those
conflicts. Such changes are treated as genuine modifications, even if
they conceptually originate from upstream changes.

As a result, approvals guarded by `approverin:already-approved-by_owners`
are **not copied** across rebases that require conflict resolution.

### Approval not copied

* A `user-backend` owner gives `Code-Review +2` on `Patch Set 3`.
* `Patch Set 4` modifies a backend-owned file.

**Result**: The approval is cleared because the owner must review the new changes.

## Label configuration

The copy condition is a `label` property and as such needs to be set in the label configuration.
The following is an example of the `approverin:already-approved-by_owners` configured for the
`Code-Review` label.

```text
[label "Code-Review"]
    function = NoBlock
    defaultValue = 0
    value = -2 This shall not be submitted
    value = -1 I would prefer this is not submitted as is
    value = 0 No score
    value = +1 Looks good to me, but someone else must approve
    value = +2 Looks good to me, approved
    copyCondition = approverin:already-approved-by_owners
```

## Customizing the copy condition behaviour with the `auto-owners-approved` in `OWNERS`

The `approverin:already-approved-by_owners` can be fine-grained enabled/disabled using the
`auto-owners-approved` configuration in the `OWNERS` file.

A vote is copied only if, after applying inheritance rules, `auto-owners-approved` is `true` for
every relevant file owned by that approver.

When the `auto-owners-approved` is set to `false`, then the copy condition is not satisfied and
therefore the scores are not copied over to the new patch-set.

Examples can be found [here](./config.md#auto-owners-approved-example).
