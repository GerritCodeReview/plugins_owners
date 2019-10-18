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
import com.google.inject.AbstractModule;
import org.eclipse.jgit.transport.ReceiveCommand.Type;
import org.junit.Test;

@TestPlugin(
    name = "owners-autoassign",
    sysModule = "com.vmware.gerrit.owners.common.GitRefListenerIT$TestModule")
public class GitRefListenerIT extends LightweightPluginDaemonTest {

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(GitReferenceUpdatedListener.class).to(GitRefListenerTest.class);
    }
  }

  @Test
  public void shouldNotProcessNoteDbOnlyRefs() {
    GitRefListenerTest gitRefListener = getPluginInstance(GitRefListenerTest.class);

    String aRefChange = RefNames.REFS_CHANGES + "01/01" + RefNames.META_SUFFIX;
    String anOldObjectId = "anOldRef";
    String aNewObjectId = "aNewRef";

    ReferenceUpdatedEventTest refUpdatedEvent =
        new ReferenceUpdatedEventTest(
            project, aRefChange, anOldObjectId, aNewObjectId, Type.CREATE);

    gitRefListener.onGitReferenceUpdated(refUpdatedEvent);
    assertEquals(0, gitRefListener.getProcessedEvents());
  }

  @Test
  public void shouldProcessRefChanges() {
    GitRefListenerTest gitRefListener = getPluginInstance(GitRefListenerTest.class);

    String aRefChange = RefNames.REFS_CHANGES + "01/01/01";
    String anOldObjectId = "anOldRef";
    String aNewObjectId = "aNewRef";

    ReferenceUpdatedEventTest refUpdatedEvent =
        new ReferenceUpdatedEventTest(
            project, aRefChange, anOldObjectId, aNewObjectId, Type.CREATE);

    gitRefListener.onGitReferenceUpdated(refUpdatedEvent);
    assertEquals(1, gitRefListener.getProcessedEvents());
  }

  private <T> T getPluginInstance(Class<T> clazz) {
    return plugin.getSysInjector().getInstance(clazz);
  }
}
