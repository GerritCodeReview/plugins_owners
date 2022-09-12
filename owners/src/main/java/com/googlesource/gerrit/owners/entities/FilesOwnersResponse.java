package com.googlesource.gerrit.owners.entities;

import java.util.Map;
import java.util.Set;

public class FilesOwnersResponse {

  private final Map<String, Set<Owner>> files;
  private final Map<Integer, Map<String, Integer>> labels;

  public FilesOwnersResponse(
      Map<Integer, Map<String, Integer>> labels, Map<String, Set<Owner>> files) {
    this.labels = labels;
    this.files = files;
  }
}
