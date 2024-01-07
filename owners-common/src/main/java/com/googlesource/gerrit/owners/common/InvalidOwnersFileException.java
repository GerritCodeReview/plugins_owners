// Copyright (C) 2024 The Android Open Source Project
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

package com.googlesource.gerrit.owners.common;

public class InvalidOwnersFileException extends Exception {
  private static final long serialVersionUID = 1L;

  public InvalidOwnersFileException(
      String project, String ownersPath, String branch, Throwable reason) {
    super(exceptionMessage(project, ownersPath, branch), reason);
  }

  private static String exceptionMessage(String project, String ownersPath, String branch) {
    return String.format(
        "Invalid owners file: %s, in project: %s, on branch %s", ownersPath, project, branch);
  }
}
