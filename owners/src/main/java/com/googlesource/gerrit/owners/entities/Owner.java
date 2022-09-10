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

import com.google.gerrit.entities.Account;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Class representing a file Owner * */
public class Owner {
  private final String name;
  private final int id;
  private final List<Map<String, Integer>> labels;

  public Owner(String name, int id, List<Map<String, Integer>> labels) {
    this.name = name;
    this.id = id;
    this.labels = labels;
  }

  public Owner(String name, int id) {
    this(name, id, new ArrayList<Map<String, Integer>>());
  }

  /**
   * Get the {@link Owner} name.
   *
   * @return the Owner name.
   */
  public String getName() {
    return name;
  }

  /**
   * Get the {@link Owner} account id.
   *
   * @return an {@code int} representation of the Owner {@link Account.Id}.
   */
  public int getId() {
    return id;
  }

  public List<Map<String, Integer>> getLabels() {
    return labels;
  }
}
