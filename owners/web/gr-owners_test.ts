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

import {shouldHide} from './gr-owners';
import {PatchRange, UserRole} from './owners-model';
import {
  ChangeInfo,
  ChangeStatus,
  SubmitRequirementResultInfo,
} from '@gerritcodereview/typescript-api/rest-api';

suite('owners status tests', () => {
  suite('shouldHide tests', () => {
    const loggedIn = getRandom(UserRole.CHANGE_OWNER, UserRole.OTHER);

    test('shouldHide - should be `true` when change is not defined', () => {
      const undefinedChange = undefined;
      const definedPatchRange = {} as unknown as PatchRange;
      assert.equal(
        shouldHide(undefinedChange, definedPatchRange, loggedIn),
        true
      );
    });

    test('shouldHide - should be `true` when patch range is not defined', () => {
      const definedChange = {} as unknown as ChangeInfo;
      const undefinedPatchRange = undefined;
      assert.equal(
        shouldHide(definedChange, undefinedPatchRange, loggedIn),
        true
      );
    });

    test('shouldHide - should be `true` when change is abandoned', () => {
      const abandonedChange = {
        status: ChangeStatus.ABANDONED,
      } as unknown as ChangeInfo;
      const definedPatchRange = {} as unknown as PatchRange;
      assert.equal(
        shouldHide(abandonedChange, definedPatchRange, loggedIn),
        true
      );
    });

    test('shouldHide - should be `true` when change is merged', () => {
      const mergedChange = {
        status: ChangeStatus.MERGED,
      } as unknown as ChangeInfo;
      const definedPatchRange = {} as unknown as PatchRange;
      assert.equal(shouldHide(mergedChange, definedPatchRange, loggedIn), true);
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
      assert.equal(shouldHide(changeWithPs2, patchRangeOnPs1, loggedIn), true);
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
      assert.equal(shouldHide(change, patchRange, loggedIn), true);
    });

    test('shouldHide - should be `true` when change has no `Owner-Approval` submit requirements', () => {
      const changeWithDifferentSubmitReqs = {
        ...change,
        submit_requirements: [
          {name: 'other'},
        ] as unknown as SubmitRequirementResultInfo[],
      };
      assert.equal(
        shouldHide(changeWithDifferentSubmitReqs, patchRange, loggedIn),
        true
      );
    });

    test('shouldHide - should be `true` when user is not logged in', () => {
      const changeWithSubmitRequirements = {
        ...change,
        submit_requirements: [
          {name: 'Owner-Approval'},
        ] as unknown as SubmitRequirementResultInfo[],
      };
      const anonymous = UserRole.ANONYMOUS;
      assert.equal(
        shouldHide(changeWithSubmitRequirements, patchRange, anonymous),
        true
      );
    });

    test('shouldHide - should be `false` when change has submit requirements and user is logged in', () => {
      const changeWithSubmitRequirements = {
        ...change,
        submit_requirements: [
          {name: 'Owner-Approval'},
        ] as unknown as SubmitRequirementResultInfo[],
      };
      assert.equal(
        shouldHide(changeWithSubmitRequirements, patchRange, loggedIn),
        false
      );
    });

    test('shouldHide - should be `false` when in edit mode', () => {
      const patchRangeWithoutPatchNum = {} as unknown as PatchRange;
      assert.equal(
        shouldHide(change, patchRangeWithoutPatchNum, loggedIn),
        false
      );
    });
  });
});

function getRandom<T>(...values: T[]): T {
  const idx = Math.floor(Math.random() * values.length);
  return values[idx];
}
