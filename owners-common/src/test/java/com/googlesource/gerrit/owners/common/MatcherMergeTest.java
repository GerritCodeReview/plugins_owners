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
import static org.junit.Assert.assertNull;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Account.Id;
import java.util.Set;
import org.junit.Test;

public class MatcherMergeTest {
  @Test
  public void mergeAutoOwnersApprovedKeepsCurrentWhenOtherUnset() {
    Matcher baseTrue = new TestMatcher(true);
    Matcher otherUnset = new TestMatcher(null);
    Matcher mergedUnsetOther = baseTrue.merge(otherUnset);
    assertThat(mergedUnsetOther.isAutoOwnersApproved()).isEqualTo(true);
  }

  @Test
  public void mergeAutoOwnersApprovedUsesOtherWhenOtherIsFalse() {
    Matcher baseUnset = new TestMatcher(null);
    Matcher otherFalse = new TestMatcher(false);
    Matcher mergedFalseOther = baseUnset.merge(otherFalse);
    assertThat(mergedFalseOther.isAutoOwnersApproved()).isEqualTo(false);
  }

  @Test
  public void mergeAutoOwnersApprovedUsesOtherWhenOtherIsTrue() {
    Matcher baseFalse = new TestMatcher(false);
    Matcher otherTrue = new TestMatcher(true);
    Matcher mergedTrueOther = baseFalse.merge(otherTrue);
    assertThat(mergedTrueOther.isAutoOwnersApproved()).isEqualTo(true);
  }

  @Test
  public void mergeAutoOwnersApprovedRemainsNullWhenBothUnset() {
    Matcher base = new TestMatcher(null);
    Matcher other = new TestMatcher(null);

    Matcher merged = base.merge(other);

    assertNull(merged.isAutoOwnersApproved());
  }

  private static class TestMatcher extends Matcher {
    private TestMatcher(Boolean autoOwnersApproved) {
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
        Set<Id> owners, Set<Id> reviewers, Set<String> groupOwners, Boolean autoOwnersApproval) {
      return new TestMatcher(autoOwnersApproval);
    }
  }
}
