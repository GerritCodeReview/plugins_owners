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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.events.GitBatchRefUpdateListener;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.googlesource.gerrit.owners.common.PathOwnersEntriesCache.OwnersRefUpdateListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class OwnersBatchRefUpdateListenerTest {
  @Parameterized.Parameters
  public static Collection<Object[]> events() {
    return Arrays.asList(
        new Object[][] {
          {mockEvent(ALL_USERS_NAME.get(), RefNames.REFS_CONFIG), 0},
          {mockEvent(AllProjectsNameProvider.DEFAULT, RefNames.REFS_CHANGES), 0},
          {mockEvent(AllProjectsNameProvider.DEFAULT, RefNames.REFS_SEQUENCES), 0},
          {mockEvent(AllProjectsNameProvider.DEFAULT, RefNames.REFS_CONFIG), 1},
          {
            mockEvent(
                AllProjectsNameProvider.DEFAULT, RefNames.REFS_CONFIG, RefNames.REFS_SEQUENCES),
            1
          },
          {mockEvent("foo", RefNames.fullName("bar")), 1},
          {mockEvent("foo", RefNames.REFS_CONFIG), 1},
          {mockEvent("foo", RefNames.REFS_CONFIG, RefNames.fullName("bar")), 2}
        });
  }

  private static AllUsersName ALL_USERS_NAME = new AllUsersName(AllUsersNameProvider.DEFAULT);

  private final GitBatchRefUpdateListener.Event input;
  private final int expectedTimes;

  public OwnersBatchRefUpdateListenerTest(
      GitBatchRefUpdateListener.Event input, int expectedTimes) {
    this.input = input;
    this.expectedTimes = expectedTimes;
  }

  @Test
  public void shouldTriggerCacheInvalidationAccordingly() {
    // given
    PathOwnersEntriesCache cachMock = mock(PathOwnersEntriesCache.class);
    OwnersRefUpdateListener listener = new OwnersRefUpdateListener(cachMock, ALL_USERS_NAME);

    // when
    listener.onGitBatchRefUpdate(input);

    // then
    verify(cachMock, times(expectedTimes)).invalidate(anyString(), anyString());
  }

  private static GitBatchRefUpdateListener.Event mockEvent(String project, String... refs) {
    GitBatchRefUpdateListener.Event eventMock = mock(GitBatchRefUpdateListener.Event.class);
    when(eventMock.getProjectName()).thenReturn(project);
    Set<GitBatchRefUpdateListener.UpdatedRef> updatedRefs =
        Stream.of(refs)
            .map(
                ref -> {
                  GitBatchRefUpdateListener.UpdatedRef updatedRef =
                      mock(GitBatchRefUpdateListener.UpdatedRef.class);
                  when(updatedRef.getRefName()).thenReturn(ref);
                  return updatedRef;
                })
            .collect(Collectors.toSet());
    when(eventMock.getUpdatedRefs()).thenReturn(updatedRefs);
    return eventMock;
  }
}
