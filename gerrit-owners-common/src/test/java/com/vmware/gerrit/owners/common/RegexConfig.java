package com.vmware.gerrit.owners.common;

import com.google.gwtorm.server.OrmException;

import java.util.Optional;

public class RegexConfig extends Config {
  protected static final int JOHN = 4;
  protected static final int JANE = 5;
  protected static final int PHILIP = 5;
  protected static final int FRANK = 6;
  protected static final int ALICE = 7;
  protected static final int BOB = 8;

  void configureMatchers() throws Exception {
    expectWrapper("OWNERS", Optional.empty());
    expectWrapper("/OWNERS",Optional.of(advancedParentConfig().getBytes()));
    expectWrapper("/project/OWNERS",Optional.of(advancedConfig().getBytes()));
    creatingPatchList("/file1.txt","/project/file2.txt");

  }
  @Override
  void resolvingEmailToAccountIdMocking() throws OrmException {

    resolveEmail("john@email.com",JOHN);
    resolveEmail("jane@email.com",JANE);
    resolveEmail("alice@email.com",ALICE);
    resolveEmail("bob@email.com",BOB);
    resolveEmail("philip@email.com",PHILIP);
    resolveEmail("frank@email.com",FRANK);


  }
  String advancedConfigFull() {
    String yamlString = ("inherited: true\n" +
        "owners: \n" +
        "- jane@email.com \n" +
        "- john@email.com \n" +
        "matches:\n" +
        "- suffix: .sql\n" +
        "  owners: [philip@email.com,frank@email.com]\n" +
        "- regex: 'Product.scala'\n" +
        "  owners: [alice@email.com,bob@email.com]"
        );
    return yamlString;
  }
  String advancedConfig() {
    String yamlString = ("inherited: true\n" +
        "owners: \n" +
        "- jane@email.com \n" +
        "matches:\n" +
        "- suffix: .sql\n" +
        "  owners:\n" +
        "  - philip@email.com\n" +
        "  - jane@email.com\n" +
        "- regex: 'Pippo.scala'\n" +
        "  owners: [ bob@email.com ]");
    return yamlString;
  }
  String advancedParentConfig() {
    String yamlString = ("inherited: false\n" +
        "owners: \n" +
        "- john@email.com \n" +
        "matches:\n" +
        "- suffix: .sql\n" +
        "  owners:\n" +
        "  - frank@email.com\n" +
        "- exact: Product.scala\n" +
        "  owners: [ alice@email.com, john@email.com ]");
    return yamlString;
  }


}
