// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners.entities;

import com.google.common.base.Objects;
import java.util.Map;
import java.util.Set;

/* Files to Owners response API representation */
public class FilesOwnersResponse {

  public final Map<String, Set<GroupOwner>> files;
  public final Map<Integer, Map<String, Integer>> ownersLabels;
  public final Map<String, Set<GroupOwner>> filesApproved;

  public FilesOwnersResponse(
      Map<Integer, Map<String, Integer>> ownersLabels,
      Map<String, Set<GroupOwner>> files,
      Map<String, Set<GroupOwner>> filesApproved) {
    this.ownersLabels = ownersLabels;
    this.files = files;
    this.filesApproved = filesApproved;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FilesOwnersResponse that = (FilesOwnersResponse) o;
    return Objects.equal(files, that.files)
        && Objects.equal(ownersLabels, that.ownersLabels)
        && Objects.equal(filesApproved, that.filesApproved);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(files, ownersLabels, filesApproved);
  }

  @Override
  public String toString() {
    return "FilesOwnersResponse{"
        + "files="
        + files
        + ", ownersLabels="
        + ownersLabels
        + ", filesApproved="
        + filesApproved
        + '}';
  }
}
