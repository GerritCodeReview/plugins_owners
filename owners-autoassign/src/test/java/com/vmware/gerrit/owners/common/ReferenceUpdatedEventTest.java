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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Ignore;

@Ignore
public class ReferenceUpdatedEventTest implements GitReferenceUpdatedListener.Event {

  private final String projectName;
  private final String ref;
  private final String oldObjectId;
  private final String newObjectId;
  private final ReceiveCommand.Type type;
  private final Account.Id eventAccountId;

  public ReferenceUpdatedEventTest(
      Project.NameKey project,
      String ref,
      String oldObjectId,
      String newObjectId,
      ReceiveCommand.Type type,
      Account.Id eventAccountId) {
    this.projectName = project.get();
    this.ref = ref;
    this.oldObjectId = oldObjectId;
    this.newObjectId = newObjectId;
    this.type = type;
    this.eventAccountId = eventAccountId;
  }

  @Override
  public String getProjectName() {
    return projectName;
  }

  @Override
  public String getRefName() {
    return ref;
  }

  @Override
  public String getOldObjectId() {
    return oldObjectId;
  }

  @Override
  public String getNewObjectId() {
    return newObjectId;
  }

  @Override
  public boolean isCreate() {
    return type == ReceiveCommand.Type.CREATE;
  }

  @Override
  public boolean isDelete() {
    return type == ReceiveCommand.Type.DELETE;
  }

  @Override
  public boolean isNonFastForward() {
    return type == ReceiveCommand.Type.UPDATE_NONFASTFORWARD;
  }

  @Override
  public AccountInfo getUpdater() {
    if (eventAccountId == null) {
      return null;
    }

    return new AccountInfo(eventAccountId.get());
  }

  @Override
  public NotifyHandling getNotify() {
    return NotifyHandling.NONE;
  }
}
