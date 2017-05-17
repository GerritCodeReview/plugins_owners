// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.vmware.gerrit.owners.common;

import com.google.gerrit.reviewdb.client.Account;
import com.google.inject.ImplementedBy;
import java.util.Set;

@ImplementedBy(AccountsImpl.class)
public interface Accounts {
  public String GROUP_PREFIX = "group/";

  Set<Account.Id> find(String nameOrEmail);
}
