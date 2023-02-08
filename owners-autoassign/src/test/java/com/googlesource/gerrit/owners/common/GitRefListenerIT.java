// Copyright (C) 2019 The Android Open Source Project
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

package com.googlesource.gerrit.owners.common;

import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.googlesource.gerrit.owners.common.ReviewerManager;

import org.eclipse.jgit.transport.ReceiveCommand.Type;
import org.junit.Test;

@TestPlugin(
    name = "owners-autoassign",
    sysModule = "com.googlesource.gerrit.owners.common.GitRefListenerIT$TestModule")
public class GitRefListenerIT extends LightweightPluginDaemonTest {

  @Inject GitRefListenerTest gitRefListener;
  @Inject ThreadLocalRequestContext requestContext;

  String aRefChange = RefNames.REFS_CHANGES + "01/01/01";
  String anOldObjectId = "anOldRef";
  String aNewObjectId = "aNewRef";

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(GitReferenceUpdatedListener.class).to(GitRefListenerTest.class);
    }
  }

  @Test
  public void shouldNotProcessNoteDbOnlyRefs() {
    ReferenceUpdatedEventTest refUpdatedEvent =
        new ReferenceUpdatedEventTest(
            project,
            RefNames.REFS_CHANGES + "01/01" + RefNames.META_SUFFIX,
            anOldObjectId,
            aNewObjectId,
            Type.CREATE,
            admin.id());

    gitRefListener.onGitReferenceUpdated(refUpdatedEvent);
    assertEquals(0, gitRefListener.getProcessedEvents());
  }

  @Test
  public void shouldProcessRefChanges() {
    gitRefListener.onGitReferenceUpdated(newRefUpdateEvent());
    assertEquals(1, gitRefListener.getProcessedEvents());
  }

  @Test
  public void shouldRetrieveChangeFromAnonymousContext() throws Exception {
    try (ManualRequestContext ctx = new ManualRequestContext(new AnonymousUser(), requestContext)) {
      gitRefListener.onGitReferenceUpdated(newRefUpdateEvent());
      assertEquals(1, gitRefListener.getProcessedEvents());
    }
  }

  @Test
  public void shouldRetrieveChangeFromAnonymousContextWithoutAccountId() throws Exception {
    ReferenceUpdatedEventTest refUpdateWithoutAccountId =
        new ReferenceUpdatedEventTest(
            project, aRefChange, anOldObjectId, aNewObjectId, Type.CREATE, null);
    try (ManualRequestContext ctx = new ManualRequestContext(new AnonymousUser(), requestContext)) {
      gitRefListener.onGitReferenceUpdated(refUpdateWithoutAccountId);
      assertEquals(1, gitRefListener.getProcessedEvents());
    }
  }

  private ReferenceUpdatedEventTest newRefUpdateEvent() {
    return new ReferenceUpdatedEventTest(
        project, aRefChange, anOldObjectId, aNewObjectId, Type.CREATE, admin.id());
  }
}
