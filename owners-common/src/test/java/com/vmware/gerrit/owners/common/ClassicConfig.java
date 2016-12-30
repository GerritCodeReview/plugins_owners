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

import org.junit.Ignore;

import java.nio.charset.Charset;
import java.util.Optional;

@Ignore
public class ClassicConfig extends Config {

  protected static final int USERA = 1;
  protected static final int USERB = 2;
  protected static final int USERC = 3;

  void configurationClassic() throws Exception {
    expectWrapper("OWNERS", Optional.empty());
    expectWrapper("classic/OWNERS",
        Optional.of(("inherited: true\n" + "owners:\n"
            + "- user-a@email.com\n" + "- user-b@email.com")
                .getBytes(Charset.defaultCharset())));

  }

  void configurationClassicInherited() throws Exception {
    expectWrapper("OWNERS",
        Optional.of(
            ("inherited: true\n" + "owners:\n" + "- user-c@email.com")
                .getBytes(Charset.defaultCharset())));

    expectWrapper("classic/OWNERS",
        Optional.of(("inherited: true\n" + "owners:\n"
            + "- user-a@email.com\n" + "- user-b@email.com")
                .getBytes(Charset.defaultCharset())));

  }

  void configurationClassicBis() throws Exception {
    String value =
        "inherited: false\n" + "owners:\n" + "- user-a@email.com";

    expectWrapper("OWNERS", Optional.of(value.getBytes()));

    String project = "inherited: true\n" + "owners:\n"
        + "- user-b@email.com\n" + "- user-c@email.com";

    expectWrapper("project/OWNERS", Optional.of(project.getBytes()));
    String sql =
        "inherited: true\n" + "owners:\n" + "- user-b@email.com";
    expectWrapper("sql/OWNERS", Optional.of(sql.getBytes()));

  }

  @Override
  public void resolvingEmailToAccountIdMocking() throws Exception {
    resolveEmail("user-a@email.com", USERA);
    resolveEmail("user-b@email.com", USERB);
    resolveEmail("user-c@email.com", USERC);

  }
}
