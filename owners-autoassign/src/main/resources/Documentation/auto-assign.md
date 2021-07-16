## Reviewers auto-assign configuration

The OWNERS file is processed by the @PLUGIN@ for automatically
assigning all relevant owners to a change for every new patch-set
uploaded.

The way that the reviewers are added is controlled by the
$GERRIT_SITE/etc/@PLUGIN@.config file.

By default, all reviewers are added synchronously when a patch-set
is uploaded. However, you may want to delay the assignment of additional
reviewers to a later stage for lowering the pressure on the Git
repository associated with concurrent updates.

For example, the following configuration would delay the assignment of
reviewers by 5 seconds:

```
[reviewers]
  async = true
  delay = 5 sec
```

See below the full list of configuration settings available:

- `reviewers.async`: assign reviewers asynchronously. When set to `false`, all
  the other settings in @PLUGIN@.config are ignored. By default, set to `false`.

- `reviewers.delay`: delay of the assignment of reviewers since the upload
  of a new patch-set, expressed in <number> <unit>. By default, set to `0`.
  
  Values should use common unit suffixes to express their setting:
  
  - ms, milliseconds

  - s, sec, second, seconds

  - m, min, minute, minutes

   - h, hr, hour, hours

- `reviewers.retryCount`: number of retries for attempting to assign reviewers
  to a change. By default, set to `2`.

- `reviewers.retryInterval`: delay between retries. Expressed in the same format
  of the `reviewers.delay`. By default, set to the same value of `reviewers.delay`.

- `reviewers.threads`: maximum concurrency of threads for assigning reviewers to
  changes. By default, set to 1.