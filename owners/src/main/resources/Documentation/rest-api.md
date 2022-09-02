# Rest API

The @PLUGIN@ exposes a Rest API endpoint to list the owners associated to each file:

```bash
GET /changes/{change-id}/revisions/{revision-id}/owners~getFilesOwners

)]}'
{
  'fileA.java': [ 'John', 'Bob'],
  'fileB.java': [ 'Mark', 'John'],
}

``` 