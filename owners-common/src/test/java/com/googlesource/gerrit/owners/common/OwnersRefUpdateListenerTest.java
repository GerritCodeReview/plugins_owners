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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.googlesource.gerrit.owners.common.PathOwnersEntriesCache.OwnersRefUpdateListener;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class OwnersRefUpdateListenerTest {
  @Parameterized.Parameters
  public static Collection<Object[]> events() {
    return Arrays.asList(
        new Object[][] {
          {mockEvent(ALL_USERS_NAME, null), false},
          {mockEvent(AllProjectsNameProvider.DEFAULT, RefNames.REFS_CHANGES), false},
          {mockEvent(AllProjectsNameProvider.DEFAULT, RefNames.REFS_SEQUENCES), false},
          {mockEvent(AllProjectsNameProvider.DEFAULT, RefNames.REFS_CONFIG), true},
          {mockEvent("foo", RefNames.fullName("bar")), true},
          {mockEvent("foo", RefNames.REFS_CONFIG), true}
        });
  }

  private static String ALL_USERS_NAME = AllUsersNameProvider.DEFAULT;

  private final GitReferenceUpdatedListener.Event input;
  private final boolean expected;

  public OwnersRefUpdateListenerTest(GitReferenceUpdatedListener.Event input, boolean expected) {
    this.input = input;
    this.expected = expected;
  }

  @Test
  public void shouldParseLabelDefinition() {
    // when
    boolean result = OwnersRefUpdateListener.supportedEvent(ALL_USERS_NAME, input);

    // then
    assertThat(result).isEqualTo(expected);
  }

  private static GitReferenceUpdatedListener.Event mockEvent(String project, String ref) {
    GitReferenceUpdatedListener.Event eventMock = mock(GitReferenceUpdatedListener.Event.class);
    when(eventMock.getProjectName()).thenReturn(project);
    when(eventMock.getRefName()).thenReturn(ref);
    return eventMock;
  }
}
