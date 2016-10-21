/*
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 */
package com.vmware.gerrit.owners.common;

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Collection;

@Singleton
public class ReviewerManager {

  private final Provider<CurrentUser> currentUserProvider;

  private final Provider<PostReviewers> postReviewersProvider;

  private final ChangeControl.GenericFactory changeControlFactory;

  private final ChangeNotes.Factory notesFactory;

  private final ChangesCollection changesCollection;

  @Inject
  public ReviewerManager(Provider<CurrentUser> currentUserProvider,
                         Provider<PostReviewers> postReviewersProvider,
                         ChangeControl.GenericFactory changeControlFactory,
                         ChangeNotes.Factory notesFactory,
                         ChangesCollection changesCollection) {
    this.currentUserProvider = currentUserProvider;
    this.postReviewersProvider = postReviewersProvider;
    this.changeControlFactory = changeControlFactory;
    this.notesFactory = notesFactory;
    this.changesCollection = changesCollection;
  }

  public void addReviewers(Change change, Collection<Account.Id> reviewers) throws ReviewerManagerException {
    try {
      PostReviewers postReviewers = postReviewersProvider.get();
      ChangeNotes notes = notesFactory.createFromIndexedChange(change);
      ChangeControl changeControl = changeControlFactory.controlFor(notes, currentUserProvider.get());
      ChangeControl changeControl = changeControlFactory.controlFor(change, currentUserProvider.get());
      ChangeResource changeResource = changesCollection.parse(changeControl);

      // HACK(vspivak): Using PostReviewers is probably inefficient here, however it has all the hook/notification
      // logic, so it's easier to call it then to mimic/copy the logic here.
      for (Account.Id accountId : reviewers) {
        AddReviewerInput input = new AddReviewerInput();
        input.reviewer = accountId.toString();
        postReviewers.apply(changeResource, input);
      }
    } catch (RestApiException e) {
      throw new ReviewerManagerException(e);
    } catch (NoSuchChangeException e) {
      throw new ReviewerManagerException(e);
    } catch (OrmException e) {
      throw new ReviewerManagerException(e);
    } catch (IOException e) {
      throw new ReviewerManagerException(e);
    } catch (Exception e) {
      throw new ReviewerManagerException(e);
    }
  }
}
