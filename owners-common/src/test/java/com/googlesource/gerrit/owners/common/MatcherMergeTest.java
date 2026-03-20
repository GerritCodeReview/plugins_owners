// Copyright (C) 2026 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.client.InheritableBoolean.FALSE;
import static com.google.gerrit.extensions.client.InheritableBoolean.INHERIT;
import static com.google.gerrit.extensions.client.InheritableBoolean.TRUE;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Account.Id;
import com.google.gerrit.extensions.client.InheritableBoolean;
import java.util.Set;
import org.junit.Test;

public class MatcherMergeTest {
  @Test
  public void mergeAutoOwnersApprovedKeepsCurrentWhenOtherUnset() {
    Matcher baseTrue = new TestMatcher(TRUE);
    Matcher otherUnset = new TestMatcher(InheritableBoolean.INHERIT);
    Matcher mergedUnsetOther = baseTrue.merge(otherUnset);
    assertThat(mergedUnsetOther.getAutoOwnersApproved()).isEqualTo(TRUE);
  }

  @Test
  public void mergeAutoOwnersApprovedUsesOtherWhenOtherIsFalse() {
    Matcher baseUnset = new TestMatcher(INHERIT);
    Matcher otherFalse = new TestMatcher(FALSE);
    Matcher mergedFalseOther = baseUnset.merge(otherFalse);
    assertThat(mergedFalseOther.getAutoOwnersApproved()).isEqualTo(FALSE);
  }

  @Test
  public void mergeAutoOwnersApprovedUsesOtherWhenOtherIsTrue() {
    Matcher baseFalse = new TestMatcher(FALSE);
    Matcher otherTrue = new TestMatcher(TRUE);
    Matcher mergedTrueOther = baseFalse.merge(otherTrue);
    assertThat(mergedTrueOther.getAutoOwnersApproved()).isEqualTo(TRUE);
  }

  @Test
  public void mergeUnsetAutoOwnersApprovedIsUnset() {
    Matcher base = new TestMatcher(INHERIT);
    Matcher other = new TestMatcher(INHERIT);

    Matcher merged = base.merge(other);

    assertThat(merged.getAutoOwnersApproved()).isEqualTo(INHERIT);
  }

  private static class TestMatcher extends Matcher {
    private TestMatcher(InheritableBoolean autoOwnersApproved) {
      super(
          "test",
          java.util.Set.of(Account.id(1)),
          java.util.Set.of(Account.id(2)),
          java.util.Set.of("grp"),
          autoOwnersApproved);
    }

    @Override
    public boolean matches(String pathToMatch) {
      return true;
    }

    @Override
    protected Matcher clone(
        Set<Id> owners,
        Set<Id> reviewers,
        Set<String> groupOwners,
        InheritableBoolean autoOwnersApproval) {
      return new TestMatcher(autoOwnersApproval);
    }
  }
}
