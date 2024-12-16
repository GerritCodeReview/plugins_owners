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

import {
  FilesOwners,
  hasOwnersSubmitRequirement,
  OwnersService,
} from './owners-service';
import {
  RequestPayload,
  RestPluginApi,
} from '@gerritcodereview/typescript-api/rest';
import {
  AccountInfo,
  ChangeInfo,
  ChangeStatus,
  HttpMethod,
} from '@gerritcodereview/typescript-api/rest-api';
import {assert} from '@open-wc/testing';
import {UserRole} from './owners-model';
import {deepEqual} from './utils';

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
    test('getLoggedInUser - returns ANONYMOUS when user not logged in', async () => {
      const notLoggedInApi = {
        getLoggedIn() {
          return Promise.resolve(false);
        },
      } as unknown as RestPluginApi;

      const service = OwnersService.getOwnersService(
        notLoggedInApi,
        fakeChange
      );
      const user = await service.getLoggedInUser();
      assert.equal(user.role, UserRole.ANONYMOUS);
      assert.equal(user.account, undefined);
    });

    test('getLoggedInUser - returns OTHER for logged in user that is NOT change owner', async () => {
      const loggedUser = account(2);
      const userLoggedInApi = {
        getLoggedIn() {
          return Promise.resolve(true);
        },
        getAccount() {
          return Promise.resolve(loggedUser);
        },
      } as unknown as RestPluginApi;
      const change = {owner: account(1)} as unknown as ChangeInfo;

      const service = OwnersService.getOwnersService(userLoggedInApi, change);
      const user = await service.getLoggedInUser();
      assert.equal(user.role, UserRole.OTHER);
      assert.equal(user.account, loggedUser);
    });

    test('getLoggedInUser - returns CHANGE_OWNER for logged in user that is a change owner', async () => {
      const owner = account(1);
      const changeOwnerLoggedInApi = {
        getLoggedIn() {
          return Promise.resolve(true);
        },
        getAccount() {
          return Promise.resolve(owner);
        },
      } as unknown as RestPluginApi;
      const change = {owner} as unknown as ChangeInfo;

      const service = OwnersService.getOwnersService(
        changeOwnerLoggedInApi,
        change
      );
      const user = await service.getLoggedInUser();
      assert.equal(user.role, UserRole.CHANGE_OWNER);
      assert.equal(user.account, owner);
    });

    test('getLoggedInUser - should fetch response from plugin only once', async () => {
      let calls = 0;
      const notLoggedInApi = {
        getLoggedIn() {
          calls++;
          return Promise.resolve(false);
        },
      } as unknown as RestPluginApi;

      const service = OwnersService.getOwnersService(
        notLoggedInApi,
        fakeChange
      );

      await service.getLoggedInUser();
      await service.getLoggedInUser();
      assert.equal(calls, 1);
    });
  });

  suite('files owners tests', () => {
    teardown(() => {
      sinon.restore();
    });

    function setupRestApiForLoggedIn(loggedIn: boolean): RestPluginApi {
      return {
        getLoggedIn() {
          return Promise.resolve(loggedIn);
        },
        send(
          _method: HttpMethod,
          _url: string,
          _payload?: RequestPayload,
          _errFn?: ErrorCallback,
          _contentType?: string
        ) {
          return Promise.resolve({});
        },
      } as unknown as RestPluginApi;
    }

    function loggedInRestApiService(acc: number): RestPluginApi {
      return {
        ...setupRestApiForLoggedIn(true),
        getAccount() {
          return Promise.resolve(account(acc));
        },
      } as unknown as RestPluginApi;
    }

    let service: OwnersService;
    let getApiStub: sinon.SinonStub;

    function setup(response = {}) {
      const acc = account(1);
      const base_change = {
        ...fakeChange,
        _number: 1,
        status: ChangeStatus.NEW,
        project: 'test_repo',
        owner: acc,
      } as unknown as ChangeInfo;
      const restApi = loggedInRestApiService(1);

      getApiStub = sinon.stub(restApi, 'send');
      getApiStub
        .withArgs(
          sinon.match.any,
          `/changes/${base_change.project}~${base_change._number}/revisions/current/owners~files-owners`,
          sinon.match.any,
          sinon.match.any
        )
        .returns(Promise.resolve(response));
      service = OwnersService.getOwnersService(restApi, base_change);
    }

    test('should call getFilesOwners', async () => {
      const expected = {
        files: {
          'AJavaFile.java': [{name: 'Bob', id: 1000001}],
          'Aptyhonfileroot.py': [
            {name: 'John', id: 1000002},
            {name: 'Bob', id: 1000001},
            {name: 'Jack', id: 1000003},
          ],
        },
        owners_labels: {
          '1000002': {
            Verified: 1,
            'Code-Review': 0,
          },
          '1000001': {
            'Code-Review': 2,
          },
        },
      };
      setup(expected);

      const response = await service.getFilesOwners();
      await flush();
      assert.equal(getApiStub.callCount, 1);
      assert.equal(
        deepEqual(response, {...expected} as unknown as FilesOwners),
        true
      );
    });

    test('should fetch response from plugin only once', async () => {
      setup();

      await service.getFilesOwners();
      await flush();

      await service.getFilesOwners();
      await flush();

      assert.equal(getApiStub.callCount, 1);
    });
  });

  suite('hasOwnersSubmitRequirement tests', () => {
    test('hasOwnersSubmitRequirement - should be `false` when change has no owners submit requirement', () => {
      const noSubmitRequirementOrRecordChange = {} as unknown as ChangeInfo;
      assert.equal(
        hasOwnersSubmitRequirement(noSubmitRequirementOrRecordChange),
        false
      );
    });

    test('hasOwnersSubmitRequirement - should be `false` when change has different submit requirements', () => {
      const differentSubmitRequirementAndRecord = {
        submit_requirements: [
          {
            submittability_expression_result: {
              expression: 'has:other_predicated',
            },
          },
        ],
      } as unknown as ChangeInfo;
      assert.equal(
        hasOwnersSubmitRequirement(differentSubmitRequirementAndRecord),
        false
      );
    });

    test('hasOwnersSubmitRequirement - should be `true` when change has owners submit requirement', () => {
      const ownersSubmitRequirement = {
        submit_requirements: [
          {
            submittability_expression_result: {
              expression: 'has:approval_owners',
            },
          },
        ],
      } as unknown as ChangeInfo;
      assert.equal(hasOwnersSubmitRequirement(ownersSubmitRequirement), true);
    });

    test('hasOwnersSubmitRequirement - should be `true` when change has owners submit rule', () => {
      const ownersSubmitRule = {
        submit_requirements: [
          {
            submittability_expression_result: {
              expression:
                'label:Code-Review from owners\u003downers~OwnersSubmitRequirement',
            },
          },
        ],
      } as unknown as ChangeInfo;
      assert.equal(hasOwnersSubmitRequirement(ownersSubmitRule), true);
    });
  });
});

function account(id: number): AccountInfo {
  return {
    _account_id: id,
  } as unknown as AccountInfo;
}

function flush() {
  return new Promise((resolve, _reject) => {
    setTimeout(resolve, 0);
  });
}
