/**
 * @license
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {assert} from '@open-wc/testing';
import {
  AccountId,
  AccountInfo,
  ChangeInfo,
  ChangeStatus,
  CommitId,
  RevisionInfo,
  EDIT,
  SubmitRequirementResultInfo,
} from '@gerritcodereview/typescript-api/rest-api';
import {ownedFiles, shouldHide} from './gr-owned-files';
import {FilesOwners, GroupOwner, Owner} from './owners-service';
import {deepEqual} from './utils';
import {User, UserRole} from './owners-model';
import {getRandom} from './test-utils';

suite('owned files tests', () => {
  suite('ownedFiles tests', () => {
    const ownerAccountId = 1;
    const owner = account(ownerAccountId);

    const ownedFile = 'README.md';
    const ownedApprovedFile = 'db.sql';
    const files = {
      [ownedFile]: [fileOwner(1)],
      'some.text': [fileOwner(6)],
    };
    const files_approved = {
      [ownedApprovedFile]: [fileOwner(1)],
    };
    const filesOwners = {
      files,
      files_approved,
    } as unknown as FilesOwners;

    test('ownedFiles - should be `undefined` when owner is `undefined`', () => {
      const undefinedOwner = undefined;
      assert.equal(ownedFiles(undefinedOwner, filesOwners), undefined);
    });

    test('ownedFiles - should be `undefined` when filesOwners are `undefined`', () => {
      const undefinedFilesOwners = undefined;
      assert.equal(ownedFiles(owner, undefinedFilesOwners), undefined);
    });

    test('ownedFiles - should return empty owned file when no files are owned by user', () => {
      const user = account(2);
      assert.equal(deepEqual(ownedFiles(user, filesOwners), []), true);
    });

    test('ownedFiles - should return owned files', () => {
      assert.equal(
        deepEqual(ownedFiles(owner, filesOwners), [
          ownedFile,
          ownedApprovedFile,
        ]),
        true
      );
    });

    test('ownedFiles - should match file owner through email without domain name', () => {
      const filesOwners = {
        files: {
          [ownedFile]: [fileOwnerWithNameOnly(`${ownerAccountId}_email`)],
          'some.text': [fileOwnerWithNameOnly('random_joe')],
        },
      } as unknown as FilesOwners;
      assert.equal(
        deepEqual(ownedFiles(owner, filesOwners), [ownedFile]),
        true
      );
    });

    test('ownedFiles - should match file owner through full name', () => {
      const filesOwners = {
        files_approved: {
          [ownedFile]: [fileOwnerWithNameOnly(`${ownerAccountId}_name`)],
          'some.text': [fileOwnerWithNameOnly('random_joe')],
        },
      } as unknown as FilesOwners;
      assert.equal(
        deepEqual(ownedFiles(owner, filesOwners), [ownedFile]),
        true
      );
    });

    test('ownedFiles - should NOT match file owner over email without domain or full name when account id is different', () => {
      const notFileOwner = {...owner, _account_id: 2 as unknown as AccountId};
      assert.equal(deepEqual(ownedFiles(notFileOwner, filesOwners), []), true);
    });
  });

  suite('shouldHide tests', () => {
    const current_revision = 'commit-1' as unknown as CommitId;
    const change = {
      status: ChangeStatus.NEW,
      submit_requirements: [
        {submittability_expression_result: {expression: 'has:approval_owners'}},
      ] as unknown as SubmitRequirementResultInfo[],
      current_revision,
    } as unknown as ChangeInfo;
    const revisionInfo = {
      _number: 1,
      commit: {commit: current_revision},
    } as unknown as RevisionInfo;
    const user = {account: account(1), role: UserRole.OTHER};
    const ownedFiles = ['README.md'];

    test('shouldHide - should be `true` when change is `undefined`', () => {
      const undefinedChange = undefined;
      assert.equal(
        shouldHide(undefinedChange, revisionInfo, user, ownedFiles),
        true
      );
    });

    test('shouldHide - should be `true` when change is `ABANDONED` or `MERGED`', () => {
      const abandonedOrMergedChange = {
        ...change,
        status: getRandom(ChangeStatus.ABANDONED, ChangeStatus.MERGED),
      };
      assert.equal(
        shouldHide(abandonedOrMergedChange, revisionInfo, user, ownedFiles),
        true
      );
    });

    test('shouldHide - should be `true` when revisionInfo is `undefined` or in `EDIT` mode', () => {
      const undefinedOrEditRevisionInfo = getRandom(undefined, {
        _number: EDIT,
      } as unknown as RevisionInfo);
      assert.equal(
        shouldHide(change, undefinedOrEditRevisionInfo, user, ownedFiles),
        true
      );
    });

    test('shouldHide - should be `true` when NOT current_revision is viewed', () => {
      const notCurrentRevisionInfo = {
        _number: 0,
        commit: {commit: 'not-current' as unknown as CommitId},
      } as unknown as RevisionInfo;
      assert.equal(
        shouldHide(change, notCurrentRevisionInfo, user, ownedFiles),
        true
      );
    });

    test('shouldHide - should be `true` when change has different submit requirements', () => {
      const changeWithOtherSubmitRequirements = {
        ...change,
        submit_requirements: [
          {
            submittability_expression_result: {
              expression: 'has:other_predicate',
            },
          },
        ] as unknown as SubmitRequirementResultInfo[],
      };
      assert.equal(
        shouldHide(
          changeWithOtherSubmitRequirements,
          revisionInfo,
          user,
          ownedFiles
        ),
        true
      );
    });

    test('shouldHide - should be `true` when user is `undefined` or `ANONYMOUS`', () => {
      const undefinedOrAnonymousUser = getRandom(undefined, {
        role: UserRole.ANONYMOUS,
      } as unknown as User);
      assert.equal(
        shouldHide(change, revisionInfo, undefinedOrAnonymousUser, ownedFiles),
        true
      );
    });

    test('shouldHide - should be `false` when user owns files', () => {
      assert.equal(shouldHide(change, revisionInfo, user, ownedFiles), false);
    });
  });
});

function account(id: number): AccountInfo {
  return {
    _account_id: id,
    email: `${id}_email@example.com`,
    name: `${id}_name`,
  } as unknown as AccountInfo;
}

function fileOwner(id: number): Owner {
  return {
    id,
    name: `${id}_name`,
  } as unknown as Owner;
}

function fileOwnerWithNameOnly(name: string): GroupOwner {
  return {name} as unknown as GroupOwner;
}
