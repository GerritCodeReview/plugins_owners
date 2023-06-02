# Rest API

The @PLUGIN@ exposes a Rest API endpoint to list the owners associated to each file that
needs a review, and, for each owner, its current labels and votes:

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