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

import {OwnersService} from './owners-service';
import {RestPluginApi} from '@gerritcodereview/typescript-api/rest';
import {ChangeInfo} from '@gerritcodereview/typescript-api/rest-api';
import {assert} from '@open-wc/testing';
import {UserRole} from './owners-model';

suite('owners service tests', () => {
  const fakeRestApi = {} as unknown as RestPluginApi;
  const fakeChange = {} as unknown as ChangeInfo;

  suite('basic api request tests', () => {
    test('getOwnersService - same change returns the same instance', () => {
      assert.equal(
        OwnersService.getOwnersService(fakeRestApi, fakeChange),
        OwnersService.getOwnersService(fakeRestApi, fakeChange)
      );
    });

    test('getOwnersService - modified change returns new instance', () => {
      assert.notEqual(
        OwnersService.getOwnersService(fakeRestApi, {...fakeChange}),
        OwnersService.getOwnersService(fakeRestApi, {...fakeChange})
      );
    });
  });

  suite('user role tests', () => {
    test('getLoggedInUserRole - returns ANONYMOUS when user not logged in', async () => {
      const notLoggedInApi = {
        getLoggedIn() {
          return Promise.resolve(false);
        },
      } as unknown as RestPluginApi;

      const service = OwnersService.getOwnersService(
        notLoggedInApi,
        fakeChange
      );
      const userRole = await service.getLoggedInUserRole();
      assert.equal(userRole, UserRole.ANONYMOUS);
    });

    test('getLoggedInUserRole - returns OTHER for logged in user that is NOT change owner', async () => {
      const userLoggedInApi = {
        getLoggedIn() {
          return Promise.resolve(true);
        },
        getAccount() {
          return Promise.resolve(account(2));
        },
      } as unknown as RestPluginApi;
      const change = {owner: account(1)} as unknown as ChangeInfo;

      const service = OwnersService.getOwnersService(userLoggedInApi, change);
      const userRole = await service.getLoggedInUserRole();
      assert.equal(userRole, UserRole.OTHER);
    });

    test('getLoggedInUserRole - returns CHANGE_OWNER for logged in user that is a change owner', async () => {
      const changeOwnerLoggedInApi = {
        getLoggedIn() {
          return Promise.resolve(true);
        },
        getAccount() {
          return Promise.resolve(account(1));
        },
      } as unknown as RestPluginApi;
      const change = {owner: account(1)} as unknown as ChangeInfo;

      const service = OwnersService.getOwnersService(
        changeOwnerLoggedInApi,
        change
      );
      const userRole = await service.getLoggedInUserRole();
      assert.equal(userRole, UserRole.CHANGE_OWNER);
    });
  });
});

function account(id: number) {
  return {
    _account_id: id,
  };
}
