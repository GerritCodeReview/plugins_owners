package com.vmware.gerrit.owners.common;

import static org.powermock.api.easymock.PowerMock.replayAll;

import com.google.common.base.Charsets;
import com.google.gwtorm.server.OrmException;

import java.nio.charset.Charset;
import java.util.Optional;

public class ClassicConfig extends Config {

  protected static final int USERA = 1;
  protected static final int USERB = 2;
  protected static final int USERC = 3;


  void configurationClassic(boolean inherit) throws Exception {
    expectWrapper("OWNERS",Optional.empty());
    if (inherit) {
      expectWrapper("/OWNERS",Optional.of(
          ("inherited: true\n" +
                      "owners:\n" +
                      "- user-c@email.com").getBytes(Charset.defaultCharset())));
    } else {
      expectWrapper("/OWNERS",Optional.empty());
    }

    expectWrapper("/classic/OWNERS", Optional.of(
        ("inherited: true\n" +
                    "owners:\n" +
                    "- user-a@email.com\n" +
                    "- user-b@email.com").getBytes(Charset.defaultCharset())));
    creatingPatchList("/classic/file.txt", null);
    replayAll();

  }
  void configurationClassicBis() throws Exception {
    expectWrapper("OWNERS",Optional.empty());
    String value = "inherited: false\n" +
    "owners:\n"+
    "- user-a@email.com";

    expectWrapper("/OWNERS",Optional.of(value.getBytes()));

    String project = "inherited: true\n"+
    "owners:\n"+
    "- user-b@email.com\n" +
    "- user-c@email.com";
    expectWrapper("/project/OWNERS",Optional.of(project.getBytes()));
    String sql = "inherited: true\n" +
    "owners:\n" +
    "- user-b@email.com";
    expectWrapper("/sql/OWNERS",Optional.of(sql.getBytes()));
    creatingPatchList("/sql/file1","/project/fileA.txt");
    replayAll();
  }

  @Override
  public void resolvingEmailToAccountIdMocking() throws Exception {

    resolveEmail("user-a@email.com",USERA);
    resolveEmail("user-b@email.com",USERB);
    resolveEmail("user-c@email.com",USERC);

  }




}
