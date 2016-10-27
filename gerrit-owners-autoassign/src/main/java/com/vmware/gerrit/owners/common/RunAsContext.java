// Copyright (C) 2016 The Android Open Source Project
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

package com.vmware.gerrit.owners.common;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.util.RequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunAsContext implements RequestContext {
  private static final Logger log = LoggerFactory.getLogger(RunAsContext.class);
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final Account.Id user;
  private final GenericFactory identifiedUserFactory;

  private ReviewDb db = null;

  public interface Factory {
    public RunAsContext create(Account.Id user);
  }

  @Inject
  public RunAsContext(SchemaFactory<ReviewDb> schemaFactory,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      @Assisted Account.Id user) {
    this.schemaFactory = schemaFactory;
    this.user = user;
    this.identifiedUserFactory = identifiedUserFactory;
  }

  @Override
  public CurrentUser getUser() {
    return identifiedUserFactory.create(user);
  }

  @Override
  public Provider<ReviewDb> getReviewDbProvider() {
    return new Provider<ReviewDb>() {
      @Override
      public ReviewDb get() {
        if (db == null) {
          try {
            db = schemaFactory.open();
          } catch (OrmException e) {
            throw new ProvisionException("Cannot open ReviewDb", e);
          }
        }
        return db;
      }
    };
  }

  public void close() {
    if (db != null) {
      try {
        db.close();
      } catch (Exception e) {
        log.error("Unable to close DB", e);
      }
    }
  }
}
