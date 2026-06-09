// Copyright (C) 2025 The Android Open Source Project
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

package com.googlesource.gerrit.owners;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.query.approval.ApprovalContext;
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder.UserInOperandFactory;
import com.google.gerrit.server.query.approval.UserInPredicate;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.owners.restapi.GetFilesOwners;

@Singleton
public class AlreadyApprovedByOperand implements UserInOperandFactory {
  public static final String OPERAND = "already-approved-by";
  private final GetFilesOwners getFilesOwners;
  private final DiffOperations diffOperations;

  public static class Module extends AbstractModule {

    @Override
    protected void configure() {
      bind(UserInOperandFactory.class)
          .annotatedWith(Exports.named(OPERAND))
          .to(AlreadyApprovedByOperand.class);
    }
  }

  public static String FULL_OPERAND_WITH_PLUGIN_NAME = String.format("%s_%s", OPERAND, "owners");

  @Inject
  AlreadyApprovedByOperand(GetFilesOwners getFilesOwners, DiffOperations diffOperations) {
    this.getFilesOwners = getFilesOwners;
    this.diffOperations = diffOperations;
  }

  @Override
  public Predicate<ApprovalContext> create(UserInPredicate.Field field) throws QueryParseException {
    return new AlreadyApprovedByPredicate(getFilesOwners, diffOperations, field);
  }
}
