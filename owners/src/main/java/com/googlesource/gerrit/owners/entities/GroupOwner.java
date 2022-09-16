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

/** Class representing a file group owner * */
public class GroupOwner {
  private final String name;

  public GroupOwner(String name) {
    this.name = name;
  }

  /**
   * Get the {@link GroupOwner} name.
   *
   * @return the group owner name.
   */
  public String getName() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GroupOwner owner = (GroupOwner) o;
    return Objects.equal(name, owner.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }

  @Override
  public String toString() {
    return "GroupOwner{" + "name='" + name + '\'' + '}';
  }
}
