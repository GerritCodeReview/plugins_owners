# Rest API

The @PLUGIN@ exposes a Rest API endpoint to list the owners associated with each file that
needs approval (`file` field), is approved (`files_approved`) and, for each owner,
its current labels and votes (`owners_labels`):

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

> __NOTE__: The API does not work in the case when custom label is in
> rules.pl configuration as described in [the config.md docs](./config.md#example-3-owners-based-on-matchers)