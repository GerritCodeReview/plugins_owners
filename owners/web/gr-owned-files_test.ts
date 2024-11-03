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
  AccountInfo,
  ChangeInfo,
  ChangeStatus,
  SubmitRequirementResultInfo,
} from '@gerritcodereview/typescript-api/rest-api';
import {ownedFiles, shouldHide} from './gr-owned-files';
import {OwnedFiles, Owner} from './owners-service';
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
  });

  suite('shouldHide tests', () => {
    const change = {
      status: ChangeStatus.NEW,
      submit_requirements: [
        {name: 'Owner-Approval'},
      ] as unknown as SubmitRequirementResultInfo[],
    } as unknown as ChangeInfo;
    const allFilesApproved = true;
    const user = {account: account(1), role: UserRole.OTHER};
    const ownedFiles = ['README.md'];

    test('shouldHide - should be `true` when change is `undefined`', () => {
      const undefinedChange = undefined;
      assert.equal(
        shouldHide(undefinedChange, !allFilesApproved, user, ownedFiles),
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
          !allFilesApproved,
          user,
          ownedFiles
        ),
        true
      );
    });

    test('shouldHide - should be `true` when all files are approved', () => {
      assert.equal(
        shouldHide(change, allFilesApproved, user, ownedFiles),
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
          !allFilesApproved,
          undefinedOrAnonymousUser,
          ownedFiles
        ),
        true
      );
    });

    test('shouldHide - should be `false` when user owns files', () => {
      assert.equal(
        shouldHide(change, !allFilesApproved, user, ownedFiles),
        false
      );
    });
  });
});

function account(id: number): AccountInfo {
  return {
    _account_id: id,
  } as unknown as AccountInfo;
}

function fileOwner(id: number): Owner {
  return {
    id,
    name: `name for account: ${id}`,
  } as unknown as Owner;
}
