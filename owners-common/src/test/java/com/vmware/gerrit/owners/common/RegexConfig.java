package com.vmware.gerrit.owners.common;

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
