# Rest API

The @PLUGIN@ exposes a Rest API endpoint to list the owners associated to each file:

```bash
GET /changes/{change-id}/revisions/{revision-id}/owners~getFilesOwners

{
  "AJavaFile.java":[ { "name":"John", "id": 1 }, { "name":"Bob", "id": 2 }],
  "Aptyhonfileroot.py":[{ "name":"John", "id": 1 }, { "name":"Bob", "id": 2 }, { "name":"John", "id": 3 }]}
}

```