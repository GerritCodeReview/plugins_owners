// Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.truth.Truth8.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LabelDefinitionTest {
  @Parameterized.Parameters
  public static Collection<Object[]> labels() {
    return Arrays.asList(
        new Object[][] {
          {null, Optional.empty()},
          {"", Optional.empty()},
          {"foo,", Optional.empty()},
          {"foo", Optional.of(new LabelDefinition("foo", null))},
          {"foo,1", Optional.of(new LabelDefinition("foo", (short) 1))},
          {"foo, 1", Optional.of(new LabelDefinition("foo", (short) 1))},
          {"foo , 1", Optional.of(new LabelDefinition("foo", (short) 1))},
          {"foo ,1 ", Optional.of(new LabelDefinition("foo", (short) 1))}
        });
  }

  private final String input;
  private final Optional<LabelDefinition> expected;

  public LabelDefinitionTest(String input, Optional<LabelDefinition> expected) {
    this.input = input;
    this.expected = expected;
  }

  @Test
  public void shouldParseLabelDefinition() {
    // when
    Optional<LabelDefinition> result = LabelDefinition.parse(input);

    // then
    assertThat(result).isEqualTo(expected);
  }
}
