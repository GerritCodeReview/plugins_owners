package com.vmware.gerrit.owners.common;

import static org.junit.Assert.*;

import org.junit.Test;

public class RegexMatcherUnit {
  @Test
  public void testRegex() {
    RegExMatcher matcher = new RegExMatcher(".*/a.*", null);
    assertTrue(matcher.matches("xxxxxx/axxxx"));
    assertFalse(matcher.matches("axxxx"));
    assertFalse(matcher.matches("xxxxx/bxxxx"));

    RegExMatcher matcher2 = new RegExMatcher("a.*.sql", null);
    assertFalse(matcher2.matches("xxxxxx/alfa.sql"));
  }

  @Test
  public void testFloatingRegex(){
    PartialRegExMatcher matcher = new PartialRegExMatcher("a.*.sql", null);
    assertTrue(matcher.matches("xxxxxxx/alfa.sql"));
    assertTrue(matcher.matches("alfa.sqlxxxxx"));
    assertFalse(matcher.matches("alfa.bar"));
  }

}
