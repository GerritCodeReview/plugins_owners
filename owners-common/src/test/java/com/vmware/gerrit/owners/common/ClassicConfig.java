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
import org.junit.Ignore;

@Ignore
public class ClassicConfig extends Config {
  public static final String USER_A_EMAIL_COM = "user-a@email.com";
  public static final String USER_B_EMAIL_COM = "user-b@email.com";
  public static final String USER_C_EMAIL_COM = "user-c@email.com";
  public static final Account.Id USER_A_ID = new Account.Id(1);
  public static final Account.Id USER_B_ID = new Account.Id(2);
  public static final Account.Id USER_C_ID = new Account.Id(3);

  @Override
  public void setup() throws Exception {
    accounts.put(USER_A_EMAIL_COM, USER_A_ID);
    accounts.put(USER_B_EMAIL_COM, USER_B_ID);
    accounts.put(USER_C_EMAIL_COM, USER_C_ID);

    super.setup();
  }
}
