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

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.client.InheritableBoolean;
import java.util.Set;
import org.junit.Test;

public class MatcherMergeTest {
  @Test
  public void mergeAutoOwnersApprovedKeepsCurrentWhenOtherUnset() {
    assertThatMatchersMergeAs(
        InheritableBoolean.TRUE, InheritableBoolean.INHERIT, InheritableBoolean.TRUE);
  }

  @Test
  public void mergeAutoOwnersApprovedUsesOtherWhenOtherIsFalse() {
    assertThatMatchersMergeAs(
        InheritableBoolean.INHERIT, InheritableBoolean.FALSE, InheritableBoolean.FALSE);
  }

  @Test
  public void mergeAutoOwnersApprovedUsesOtherWhenOtherIsTrue() {
    assertThatMatchersMergeAs(
        InheritableBoolean.FALSE, InheritableBoolean.TRUE, InheritableBoolean.TRUE);
  }

  @Test
  public void mergeUnsetAutoOwnersApprovedIsUnset() {
    assertThatMatchersMergeAs(
        InheritableBoolean.INHERIT, InheritableBoolean.INHERIT, InheritableBoolean.INHERIT);
  }

  private void assertThatMatchersMergeAs(
      InheritableBoolean baseMatcherBool,
      InheritableBoolean otherMatcherBool,
      InheritableBoolean mergedMatcherBool) {
    Matcher baseTrue = new TestMatcher(baseMatcherBool);
    Matcher otherUnset = new TestMatcher(otherMatcherBool);
    Matcher mergedUnsetOther = baseTrue.merge(otherUnset);
    assertThat(mergedUnsetOther.getAutoOwnersApproved()).isEqualTo(mergedMatcherBool);
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
        Set<Account.Id> owners,
        Set<Account.Id> reviewers,
        Set<String> groupOwners,
        InheritableBoolean autoOwnersApproval) {
      return new TestMatcher(autoOwnersApproval);
    }
  }
}
