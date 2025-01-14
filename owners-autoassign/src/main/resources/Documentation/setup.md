## Setup

The owners-autoassign plugin depends on the shared library `owners-api.jar`
which needs to be installed into the `$GERRIT_SITE/lib`. You will then need to
add the following entry to `gerrit.config`:

```
[gerrit]
  installModule = com.googlesource.gerrit.owners.api.OwnersApiModule
```

A restart of the Gerrit service will be required for the lib to be loaded.

Once the `owners-api.jar` is loaded at Gerrit startup, the `owners-autoassign.jar`
file can be installed like a regular Gerrit plugin, by simply placing the file
in the `GERRIT_SITE/plugins` directory or installed through the plugin manager.