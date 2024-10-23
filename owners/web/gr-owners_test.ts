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

import {getFileOwnership, shouldHide} from './gr-owners';
import {FileOwnership, FileStatus, PatchRange, UserRole} from './owners-model';
import {
  ChangeInfo,
  ChangeStatus,
  SubmitRequirementResultInfo,
} from '@gerritcodereview/typescript-api/rest-api';
import {FilesOwners} from './owners-service';
import {deepEqual} from './utils';

suite('owners status tests', () => {
  const allFilesApproved = true;

  suite('shouldHide tests', () => {
    const loggedIn = getRandom(UserRole.CHANGE_OWNER, UserRole.OTHER);

    test('shouldHide - should be `true` when change is not defined', () => {
      const undefinedChange = undefined;
      const definedPatchRange = {} as unknown as PatchRange;
      assert.equal(
        shouldHide(
          undefinedChange,
          definedPatchRange,
          allFilesApproved,
          loggedIn
        ),
        true
      );
    });

    test('shouldHide - should be `true` when patch range is not defined', () => {
      const definedChange = {} as unknown as ChangeInfo;
      const undefinedPatchRange = undefined;
      assert.equal(
        shouldHide(
          definedChange,
          undefinedPatchRange,
          allFilesApproved,
          loggedIn
        ),
        true
      );
    });

    test('shouldHide - should be `true` when change is abandoned', () => {
      const abandonedChange = {
        status: ChangeStatus.ABANDONED,
      } as unknown as ChangeInfo;
      const definedPatchRange = {} as unknown as PatchRange;
      assert.equal(
        shouldHide(
          abandonedChange,
          definedPatchRange,
          allFilesApproved,
          loggedIn
        ),
        true
      );
    });

    test('shouldHide - should be `true` when change is merged', () => {
      const mergedChange = {
        status: ChangeStatus.MERGED,
      } as unknown as ChangeInfo;
      const definedPatchRange = {} as unknown as PatchRange;
      assert.equal(
        shouldHide(mergedChange, definedPatchRange, allFilesApproved, loggedIn),
        true
      );
    });

    test('shouldHide - should be `true` if not on the latest PS', () => {
      const changeWithPs2 = {
        status: ChangeStatus.NEW,
        revisions: {
          current_rev: {_number: 2},
        },
        current_revision: 'current_rev',
      } as unknown as ChangeInfo;
      const patchRangeOnPs1 = {patchNum: 1} as unknown as PatchRange;
      assert.equal(
        shouldHide(changeWithPs2, patchRangeOnPs1, allFilesApproved, loggedIn),
        true
      );
    });

    const change = {
      status: ChangeStatus.NEW,
      revisions: {
        current_rev: {_number: 1},
      },
      current_revision: 'current_rev',
    } as unknown as ChangeInfo;
    const patchRange = {patchNum: 1} as unknown as PatchRange;

    test('shouldHide - should be `true` when change has no submit requirements', () => {
      assert.equal(
        shouldHide(change, patchRange, !allFilesApproved, loggedIn),
        true
      );
    });

    test('shouldHide - should be `true` when change has no `Owner-Approval` submit requirements', () => {
      const changeWithDifferentSubmitReqs = {
        ...change,
        submit_requirements: [
          {name: 'other'},
        ] as unknown as SubmitRequirementResultInfo[],
      };
      assert.equal(
        shouldHide(
          changeWithDifferentSubmitReqs,
          patchRange,
          !allFilesApproved,
          loggedIn
        ),
        true
      );
    });

    const changeWithSubmitRequirements = {
      ...change,
      submit_requirements: [
        {name: 'Owner-Approval'},
      ] as unknown as SubmitRequirementResultInfo[],
    };

    test('shouldHide - should be `true` when user is not change owner', () => {
      const anonymous = UserRole.ANONYMOUS;
      assert.equal(
        shouldHide(
          changeWithSubmitRequirements,
          patchRange,
          !allFilesApproved,
          anonymous
        ),
        true
      );
    });

    test('shouldHide - should be `true` when change has submit requirements and has all files approved even if user is logged in', () => {
      assert.equal(
        shouldHide(
          changeWithSubmitRequirements,
          patchRange,
          allFilesApproved,
          loggedIn
        ),
        true
      );
    });

    test('shouldHide - should be `false` when change has submit requirements, has no all files approved and user is logged in', () => {
      assert.equal(
        shouldHide(
          changeWithSubmitRequirements,
          patchRange,
          !allFilesApproved,
          loggedIn
        ),
        false
      );
    });

    test('shouldHide - should be `false` when in edit mode', () => {
      const patchRangeWithoutPatchNum = {} as unknown as PatchRange;
      assert.equal(
        shouldHide(
          change,
          patchRangeWithoutPatchNum,
          allFilesApproved,
          loggedIn
        ),
        false
      );
    });
  });

  suite('getFileOwnership tests', () => {
    const path = 'readme.md';
    const emptyFilesOwners = {} as unknown as FilesOwners;
    const fileOwnersWithPath = {
      files: {[path]: [{name: 'John', id: 1}]},
    } as unknown as FilesOwners;

    test('getFileOwnership - should be `undefined` when path is `undefined', () => {
      const undefinedPath = undefined;
      assert.equal(
        getFileOwnership(undefinedPath, allFilesApproved, emptyFilesOwners),
        undefined
      );
    });

    test('getFileOwnership - should be `undefined` when file owners are `undefined', () => {
      const undefinedFileOwners = undefined;
      assert.equal(
        getFileOwnership(path, allFilesApproved, undefinedFileOwners),
        undefined
      );
    });

    test('getFileOwnership - should return `FileOwnership` with `NOT_OWNED_OR_APPROVED` fileStatus when `allFilesApproved`', () => {
      assert.equal(
        deepEqual(
          getFileOwnership(path, allFilesApproved, fileOwnersWithPath),
          {fileStatus: FileStatus.NOT_OWNED_OR_APPROVED} as FileOwnership
        ),
        true
      );
    });

    test('getFileOwnership - should return `FileOwnership` with `NOT_OWNED_OR_APPROVED` fileStatus when file has no owner', () => {
      assert.equal(
        deepEqual(getFileOwnership(path, !allFilesApproved, emptyFilesOwners), {
          fileStatus: FileStatus.NOT_OWNED_OR_APPROVED,
        } as FileOwnership),
        true
      );
    });

    test('getFileOwnership - should return `FileOwnership` with `NEEDS_APPROVAL` fileStatus when file has owner', () => {
      assert.equal(
        deepEqual(
          getFileOwnership(path, !allFilesApproved, fileOwnersWithPath),
          {fileStatus: FileStatus.NEEDS_APPROVAL} as FileOwnership
        ),
        true
      );
    });
  });
});

function getRandom<T>(...values: T[]): T {
  const idx = Math.floor(Math.random() * values.length);
  return values[idx];
}
