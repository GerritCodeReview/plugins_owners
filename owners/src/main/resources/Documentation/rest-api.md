# Rest API

The @PLUGIN@ exposes a Rest API endpoint to list the owners associated with each file that
needs approval (`files`), is approved (`files_approved`), is approved through
`auto-owners-approved` (`files_auto_approved`) and, for each owner, its current labels and
votes (`owners_labels`):

```bash
GET /changes/{change-id}/revisions/{revision-id}/owners~files-owners

{
  "files": {
    "AJavaFile.java":[
      { "name":"John", "id": 1000002 },
      { "name":"Bob", "id": 1000001 }
    ],
    "Aptyhonfileroot.py":[
      { "name":"John", "id": 1000002 },
      { "name":"Bob", "id": 1000001 },
      { "name":"Jack", "id": 1000003 }
    ]
  },
  "files_approved": {
    "NewBuild.build":[
      { "name":"John", "id": 1000004 },
      { "name":"Release Engineer", "id": 1000001 }
    ]
  },
  "files_auto_approved": {
    "Repository.java":[
      { "name":"John", "id": 1000004 },
      { "name":"Release Engineer", "id": 1000001 }
    ]
  },
  "owners_labels" : {
    "1000002": {
      "Verified": 1,
      "Code-Review": 0
    },
    "1000001": {
      "Code-Review": -2
    }
  }
}

```

`files_auto_approved` contains the files whose approval on the current patch set comes from a vote
that was copied forward because the `auto-owners-approved` logic applies.
See [the relevant section](./copy-conditions.md#auto-owners-approved) for more details on this.

`files_auto_approved` and `files_approved` are mutually exclusive. A file that is auto-approved is
returned only in `files_auto_approved`, and both sections return the full owner set for each file.
If a file also has a sufficient explicit owner vote on the current patch set, it is treated as
explicitly approved and returned only in `files_approved`.

> __NOTE__: The API does not work in the case when custom label is in
> rules.pl configuration as described in [the config.md docs](https://gerrit.googlesource.com/plugins/owners/+/refs/heads/stable-3.4/owners/src/main/resources/Documentation/config.md#example-3-owners-file-without-matchers-and-custom-owner_approves-label)