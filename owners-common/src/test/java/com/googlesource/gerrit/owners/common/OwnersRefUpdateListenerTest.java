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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.AllUsersName;
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
          {mockEvent(ALL_USERS_NAME.get(), null), 0},
          {mockEvent(AllProjectsNameProvider.DEFAULT, RefNames.REFS_CHANGES), 0},
          {mockEvent(AllProjectsNameProvider.DEFAULT, RefNames.REFS_SEQUENCES), 0},
          {mockEvent(AllProjectsNameProvider.DEFAULT, RefNames.REFS_CONFIG), 1},
          {mockEvent("foo", RefNames.fullName("bar")), 1},
          {mockEvent("foo", RefNames.REFS_CONFIG), 1}
        });
  }

  private static AllUsersName ALL_USERS_NAME = new AllUsersName(AllUsersNameProvider.DEFAULT);

  private final GitReferenceUpdatedListener.Event input;
  private final int expectedTimes;

  public OwnersRefUpdateListenerTest(GitReferenceUpdatedListener.Event input, int expectedTimes) {
    this.input = input;
    this.expectedTimes = expectedTimes;
  }

  @Test
  public void shouldTriggerCacheInvalidationAccordingly() {
    // given
    PathOwnersEntriesCache cachMock = mock(PathOwnersEntriesCache.class);
    OwnersRefUpdateListener listener = new OwnersRefUpdateListener(cachMock, ALL_USERS_NAME);

    // when
    listener.onGitReferenceUpdated(input);

    // then
    verify(cachMock, times(expectedTimes)).invalidate(input.getProjectName(), input.getRefName());
  }

  private static GitReferenceUpdatedListener.Event mockEvent(String project, String ref) {
    GitReferenceUpdatedListener.Event eventMock = mock(GitReferenceUpdatedListener.Event.class);
    when(eventMock.getProjectName()).thenReturn(project);
    when(eventMock.getRefName()).thenReturn(ref);
    return eventMock;
  }
}
