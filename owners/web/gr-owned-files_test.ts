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
  RevisionInfo,
  EDIT,
  SubmitRequirementResultInfo,
} from '@gerritcodereview/typescript-api/rest-api';
import {ownedFiles, shouldHide} from './gr-owned-files';
import {GroupOwner, OwnedFiles, Owner} from './owners-service';
import {deepEqual} from './utils';
import {User, UserRole} from './owners-model';
import {getRandom} from './test-utils';

suite('owned files tests', () => {
  suite('ownedFiles tests', () => {
    const ownerAccountId = 1;
    const owner = account(ownerAccountId);

    const ownedFile = 'README.md';
    const files = {
      [ownedFile]: [fileOwner(1)],
      'some.text': [fileOwner(6)],
    } as unknown as OwnedFiles;

    test('ownedFiles - should be `undefined` when owner is `undefined`', () => {
      const undefinedOwner = undefined;
      assert.equal(ownedFiles(undefinedOwner, files), undefined);
    });

    test('ownedFiles - should be `undefined` when files are `undefined`', () => {
      const undefinedFiles = undefined;
      assert.equal(ownedFiles(owner, undefinedFiles), undefined);
    });

    test('ownedFiles - should return empty owned file when no files are owned by user', () => {
      const user = account(2);
      assert.equal(deepEqual(ownedFiles(user, files), []), true);
    });

    test('ownedFiles - should return owned files', () => {
      assert.equal(deepEqual(ownedFiles(owner, files), [ownedFile]), true);
    });

    test('ownedFiles - should match file owner through email without domain name', () => {
      const files = {
        [ownedFile]: [fileOwnerWithNameOnly(`${ownerAccountId}_email`)],
        'some.text': [fileOwnerWithNameOnly('random_joe')],
      } as unknown as OwnedFiles;
      assert.equal(deepEqual(ownedFiles(owner, files), [ownedFile]), true);
    });

    test('ownedFiles - should match file owner through full name', () => {
      const files = {
        [ownedFile]: [fileOwnerWithNameOnly(`${ownerAccountId}_name`)],
        'some.text': [fileOwnerWithNameOnly('random_joe')],
      } as unknown as OwnedFiles;
      assert.equal(deepEqual(ownedFiles(owner, files), [ownedFile]), true);
    });

    test('ownedFiles - should NOT match file owner over email without domain or full name when account id is different', () => {
      const notFileOwner = {...owner, _account_id: 2 as unknown as AccountId};
      assert.equal(deepEqual(ownedFiles(notFileOwner, files), []), true);
    });
  });

  suite('shouldHide tests', () => {
    const change = {
      status: ChangeStatus.NEW,
      submit_requirements: [
        {name: 'Owner-Approval'},
      ] as unknown as SubmitRequirementResultInfo[],
    } as unknown as ChangeInfo;
    const revisionInfo = {_number: 1} as unknown as RevisionInfo;
    const allFilesApproved = true;
    const user = {account: account(1), role: UserRole.OTHER};
    const ownedFiles = ['README.md'];

    test('shouldHide - should be `true` when change is `undefined`', () => {
      const undefinedChange = undefined;
      assert.equal(
        shouldHide(
          undefinedChange,
          revisionInfo,
          !allFilesApproved,
          user,
          ownedFiles
        ),
        true
      );
    });

    test('shouldHide - should be `true` when change is `ABANDONED` or `MERGED`', () => {
      const abandonedOrMergedChange = {
        ...change,
        status: getRandom(ChangeStatus.ABANDONED, ChangeStatus.MERGED),
      };
      assert.equal(
        shouldHide(
          abandonedOrMergedChange,
          revisionInfo,
          !allFilesApproved,
          user,
          ownedFiles
        ),
        true
      );
    });

    test('shouldHide - should be `true` when revisionInfo is `undefined` or in `EDIT` mode', () => {
      const undefinedOrEditRevisionInfo = getRandom(undefined, {
        _number: EDIT,
      } as unknown as RevisionInfo);
      assert.equal(
        shouldHide(
          change,
          undefinedOrEditRevisionInfo,
          !allFilesApproved,
          user,
          ownedFiles
        ),
        true
      );
    });

    test('shouldHide - should be `true` when change has different submit requirements', () => {
      const changeWithOtherSubmitRequirements = {
        ...change,
        submit_requirements: [
          {name: 'Other'},
        ] as unknown as SubmitRequirementResultInfo[],
      };
      assert.equal(
        shouldHide(
          changeWithOtherSubmitRequirements,
          revisionInfo,
          !allFilesApproved,
          user,
          ownedFiles
        ),
        true
      );
    });

    test('shouldHide - should be `true` when all files are approved', () => {
      assert.equal(
        shouldHide(change, revisionInfo, allFilesApproved, user, ownedFiles),
        true
      );
    });

    test('shouldHide - should be `true` when user is `undefined` or `ANONYMOUS`', () => {
      const undefinedOrAnonymousUser = getRandom(undefined, {
        role: UserRole.ANONYMOUS,
      } as unknown as User);
      assert.equal(
        shouldHide(
          change,
          revisionInfo,
          !allFilesApproved,
          undefinedOrAnonymousUser,
          ownedFiles
        ),
        true
      );
    });

    test('shouldHide - should be `false` when user owns files', () => {
      assert.equal(
        shouldHide(change, revisionInfo, !allFilesApproved, user, ownedFiles),
        false
      );
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
