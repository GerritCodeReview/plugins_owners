package com.vmware.gerrit.owners.common;

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.reviewdb.client.Project;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Ignore;

@Ignore
public class TestReferenceUpdatedEvent implements GitReferenceUpdatedListener.Event {

  private final String projectName;
  private final String ref;
  private final String oldObjectId;
  private final String newObjectId;
  private final ReceiveCommand.Type type;

  public TestReferenceUpdatedEvent(
      Project.NameKey project,
      String ref,
      String oldObjectId,
      String newObjectId,
      ReceiveCommand.Type type) {
    this.projectName = project.get();
    this.ref = ref;
    this.oldObjectId = oldObjectId;
    this.newObjectId = newObjectId;
    this.type = type;
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
    return null;
  }

  @Override
  public NotifyHandling getNotify() {
    return NotifyHandling.NONE;
  }
}
