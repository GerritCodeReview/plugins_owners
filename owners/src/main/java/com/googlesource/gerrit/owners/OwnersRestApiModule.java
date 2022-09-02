package com.googlesource.gerrit.owners;

import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.change.RevisionResource;
import com.googlesource.gerrit.owners.restapi.GetFilesOwners;

public class OwnersRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    get(RevisionResource.REVISION_KIND, "files-owners").to(GetFilesOwners.class);
  }
}
