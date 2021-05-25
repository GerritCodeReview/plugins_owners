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

package com.vmware.gerrit.owners.common;

import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.googlesource.gerrit.owners.api.OwnersApiModule;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.transport.ReceiveCommand.Type;
import org.junit.Test;

@TestPlugin(
    name = "owners-autoassign",
    sysModule = "com.vmware.gerrit.owners.common.GitRefListenerIT$TestModule")
public class GitRefListenerIT extends LightweightPluginDaemonTest {

  @Inject DynamicSet<GitReferenceUpdatedListener> allRefUpdateListeners;
  @Inject ThreadLocalRequestContext requestContext;

  String anOldObjectId = "anOldRef";
  String aNewObjectId = "aNewRef";

  @Override
  public Module createModule() {
    return new OwnersApiModule();
  }

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      DynamicSet.bind(binder(), GitReferenceUpdatedListener.class).to(GitRefListenerTest.class);
    }
  }

  @Test
  public void shouldNotProcessNoteDbOnlyRefs() throws Exception {
    String changeRefPrefix = createChange().getChange().getId().toRefPrefix();
    int baselineProcessedEvents = gitRefListener().getProcessedEvents();

    ReferenceUpdatedEventTest refUpdatedEvent =
        new ReferenceUpdatedEventTest(
            project,
            changeRefPrefix + RefNames.META_SUFFIX.substring(1),
            anOldObjectId,
            aNewObjectId,
            Type.CREATE,
            admin.id());

    gitRefListener().onGitReferenceUpdated(refUpdatedEvent);
    assertEquals(baselineProcessedEvents, gitRefListener().getProcessedEvents());
  }

  @Test
  public void shoulProcessSetReadyForReviewOnNoteDb() throws Exception {
    int wipChangeNum = createChange().getChange().getId().get();
    gApi.changes().id(wipChangeNum).setWorkInProgress();

    int baselineProcessedEvents = gitRefListener().getProcessedEvents();

    gApi.changes().id(wipChangeNum).setReadyForReview();
    assertEquals(1, gitRefListener().getProcessedEvents() - baselineProcessedEvents);
  }

  @Test
  public void shoulNotProcessEmptyMetaRefUpdatesAfterSetReadyForReviewOnNoteDb() throws Exception {
    int wipChangeNum = createChange().getChange().getId().get();
    gApi.changes().id(wipChangeNum).setWorkInProgress();
    gApi.changes().id(wipChangeNum).setReadyForReview();
    int baselineProcessedEvents = gitRefListener().getProcessedEvents();

    gApi.changes().id(wipChangeNum).addReviewer("user");
    assertEquals(baselineProcessedEvents, gitRefListener().getProcessedEvents());
  }

  @Test
  public void shouldProcessRefChanges() throws Exception {
    gitRefListener().onGitReferenceUpdated(newRefUpdateEvent());
    assertEquals(1, gitRefListener().getProcessedEvents());
  }

  @Test
  public void shouldRetrieveChangeFromAnonymousContext() throws Exception {
    try (ManualRequestContext ctx = new ManualRequestContext(new AnonymousUser(), requestContext)) {
      gitRefListener().onGitReferenceUpdated(newRefUpdateEvent());
      assertEquals(1, gitRefListener().getProcessedEvents());
    }
  }

  @Test
  public void shouldRetrieveChangeFromAnonymousContextWithoutAccountId() throws Exception {
    String refPrefix = createChange().getChange().getId().toRefPrefix();
    ReferenceUpdatedEventTest refUpdateWithoutAccountId =
        new ReferenceUpdatedEventTest(
            project, refPrefix, anOldObjectId, aNewObjectId, Type.CREATE, null);
    try (ManualRequestContext ctx = new ManualRequestContext(new AnonymousUser(), requestContext)) {
      gitRefListener().onGitReferenceUpdated(refUpdateWithoutAccountId);
      assertEquals(1, gitRefListener().getProcessedEvents());
    }
  }

  private GitRefListenerTest gitRefListener() {
    return (GitRefListenerTest)
        StreamSupport.stream(allRefUpdateListeners.entries().spliterator(), false)
            .map(Extension::get)
            .filter(listener -> GitRefListenerTest.class.isAssignableFrom(listener.getClass()))
            .findFirst()
            .get();
  }

  private ReferenceUpdatedEventTest newRefUpdateEvent() throws Exception {
    String refPrefix = createChange().getChange().getId().toRefPrefix();
    return new ReferenceUpdatedEventTest(
        project, refPrefix, anOldObjectId, aNewObjectId, Type.CREATE, admin.id());
  }
}
