// Copyright (C) 2021 The Android Open Source Project
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

package com.googlesource.gerrit.owners.api;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.common.ChangeInfo;
import java.util.Collection;

/** API to expose a mechanism to selectively add owners to the attention-set. */
public interface OwnersAttentionSet {

  /**
   * Select the owners that should be added to the attention-set.
   *
   * @param changeInfo change under review
   * @param owners set of owners associated with a change.
   * @return subset of owners that need to be added to the attention-set.
   */
  Collection<Account.Id> addToAttentionSet(ChangeInfo changeInfo, Collection<Account.Id> owners);
}
