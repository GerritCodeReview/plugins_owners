package com.vmware.gerrit.owners.common;

//Copyright (C) 2017 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

import com.google.gwtorm.server.OrmException;

import org.junit.Ignore;

@Ignore
public class RegexConfig extends Config {
  protected static final int JOHN = 4;
  protected static final int JANE = 5;
  protected static final int PHILIP = 6;
  protected static final int FRANK = 7;
  protected static final int ALICE = 8;
  protected static final int BOB = 9;

  public static String ADVANCED_CONFIG_FULL =
      "inherited: true\n" + "owners: \n" + "- jane@email.com \n"
          + "- john@email.com \n" + "matches:\n" + "- suffix: .sql\n"
          + "  owners: [philip@email.com,frank@email.com]\n"
          + "- regex: 'Product.scala'\n"
          + "  owners: [alice@email.com,bob@email.com]";

  public static String ADVANCED_PARENT_CONFIG =
      "inherited: false\n" + "owners: \n" + "- john@email.com \n"
          + "matches:\n" + "- suffix: .sql\n" + "  owners:\n"
          + "  - frank@email.com\n" + "- exact: Product.scala\n"
          + "  owners: [ alice@email.com, john@email.com ]";

  public static String ADVANCED_CONFIG = "inherited: true\n" + "owners: \n"
        + "- jane@email.com \n" + "matches:\n" + "- regex: .*/a.*\n"
        + "  owners: [ philip@email.com, jane@email.com] \n"
        + "- exact: file1.txt\n" + "  owners: [ bob@email.com ]";


  @Override
  void resolvingEmailToAccountIdMocking() throws OrmException {
    resolveEmail("john@email.com", JOHN);
    resolveEmail("jane@email.com", JANE);
    resolveEmail("alice@email.com", ALICE);
    resolveEmail("bob@email.com", BOB);
    resolveEmail("philip@email.com", PHILIP);
    resolveEmail("frank@email.com", FRANK);
  }
}
