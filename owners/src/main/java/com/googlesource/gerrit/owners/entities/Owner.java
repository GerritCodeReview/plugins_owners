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
import com.google.gerrit.entities.Account;

/** Class representing a file Owner * */
public class Owner extends GroupOwner {
  private final int id;

  public Owner(String name, int id) {
    super(name);
    this.id = id;
  }

  /**
   * Get the {@link Owner} account id.
   *
   * @return an {@code int} representation of the Owner {@link Account.Id}.
   */
  public int getId() {
    return id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Owner owner = (Owner) o;
    return Objects.equal(id, owner.id) && Objects.equal(getName(), owner.getName());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getName(), id);
  }

  @Override
  public String toString() {
    return "Owner{" + "id=" + id + '}';
  }
}
