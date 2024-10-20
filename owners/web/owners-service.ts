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

import {RestPluginApi} from '@gerritcodereview/typescript-api/rest';
import {
  AccountDetailInfo,
  ChangeInfo,
} from '@gerritcodereview/typescript-api/rest-api';
import {UserRole} from './owners-model';

class OwnersApi {
  constructor(readonly restApi: RestPluginApi) {}

  async getAccount(): Promise<AccountDetailInfo | undefined> {
    const loggedIn = await this.restApi.getLoggedIn();
    if (!loggedIn) return undefined;
    return await this.restApi.getAccount();
  }
}

let service: OwnersService | undefined;

export class OwnersService {
  private api: OwnersApi;

  constructor(readonly restApi: RestPluginApi, readonly change: ChangeInfo) {
    this.api = new OwnersApi(restApi);
  }

  async getLoggedInUserRole(): Promise<UserRole> {
    const account = await this.api.getAccount();
    if (!account) {
      return UserRole.ANONYMOUS;
    }
    if (this.change.owner._account_id === account._account_id) {
      return UserRole.CHANGE_OWNER;
    }
    return UserRole.OTHER;
  }

  static getOwnersService(restApi: RestPluginApi, change: ChangeInfo) {
    if (!service || service.change !== change) {
      service = new OwnersService(restApi, change);
    }
    return service;
  }
}
