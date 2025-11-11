@PLUGIN@ CopyCondition
======================

The @PLUGIN@ plugin provides an
additional [copy condition](https://gerrit-review.googlesource.com/Documentation/config-labels.html#label_copyCondition)
operand that can be used in label definitions to control when owner approvals should be preserved
across patch sets.

This document describes the behavior of the operand, its purpose, and how to
configure it.

## uploaderin:already-approved-by_owners

This operand enables approval copying based on file ownership and patch-set differences.
It ensures that owner approvals are carried forward only when the new patch set does not affect the
areas the approver is responsible for.

By doing so, it reduces unnecessary re-review, allowing owners to retain their previous approval
when their files are unchanged.

When evaluating a label's `copyCondition`, the operand `uploaderin:already-approved-by_owners`
returns true only when:

1. The approval was previously given by a user who owns at least one file in the change; and
2. The newly uploaded patch set does not modify any files owned by that user.

If a patch set updates files that the approver owns, the approval is cleared and the owner is
expected to review the new changes.

## Example Scenarios

Given an `OWNERS` file that split ownerships between `frontend` users and `backend` users based on
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
