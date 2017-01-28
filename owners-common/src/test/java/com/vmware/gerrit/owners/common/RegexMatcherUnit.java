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
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
