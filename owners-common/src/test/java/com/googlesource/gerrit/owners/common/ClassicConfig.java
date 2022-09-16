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

package com.googlesource.gerrit.owners.common;

import com.google.gerrit.entities.Account;
import org.junit.Ignore;

@Ignore
public class ClassicConfig extends Config {
  public static final String USER_A = "user-a";
  public static final String USER_B = "user-b";
  public static final String USER_C = "user-c";
  public static final String EMAIL_DOMAIN = "@email.com";
  public static final String USER_A_EMAIL_COM = USER_A + EMAIL_DOMAIN;
  public static final String USER_B_EMAIL_COM = USER_B + EMAIL_DOMAIN;
  public static final String USER_C_EMAIL_COM = USER_C + EMAIL_DOMAIN;
  public static final Account.Id USER_A_ID = Account.id(1);
  public static final Account.Id USER_B_ID = Account.id(2);
  public static final Account.Id USER_C_ID = Account.id(3);

  @Override
  public void setup() throws Exception {
    accounts.put(USER_A_EMAIL_COM, USER_A_ID);
    accounts.put(USER_B_EMAIL_COM, USER_B_ID);
    accounts.put(USER_C_EMAIL_COM, USER_C_ID);

    super.setup();
  }
}
