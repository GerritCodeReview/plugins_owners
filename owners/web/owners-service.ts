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

import {HttpMethod, RestPluginApi} from '@gerritcodereview/typescript-api/rest';
import {
  AccountDetailInfo,
  ChangeInfo,
  NumericChangeId,
  RepoName,
} from '@gerritcodereview/typescript-api/rest-api';
import {User, UserRole} from './owners-model';

export interface GroupOwner {
  name: string;
}

export interface Owner extends GroupOwner {
  id: number;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function isOwner(o: any): o is Owner {
  return o && typeof o.id === 'number' && typeof o.name === 'string';
}

export type FileOwner = Owner | GroupOwner;

export interface OwnedFiles {
  [fileName: string]: FileOwner[];
}

export interface OwnersLabels {
  [id: string]: {
    [label: string]: number;
  };
}

export interface FilesOwners {
  files: OwnedFiles;
  files_approved: OwnedFiles;
  owners_labels: OwnersLabels;
}

const OWNERS_SUBMIT_REQUIREMENT = 'Owner-Approval';
const OWNERS_SUBMIT_RECORD = 'owners~OwnersSubmitRequirement';

export function hasOwnersSubmitRequirementOrRecord(
  change: ChangeInfo
): boolean {
  return (
    (change.submit_requirements !== undefined &&
      change.submit_requirements.find(
        r => r.name === OWNERS_SUBMIT_REQUIREMENT
      ) !== undefined) ||
    (change.submit_records !== undefined &&
      change.submit_records.find(r => r.rule_name === OWNERS_SUBMIT_RECORD) !==
        undefined)
  );
}

class ResponseError extends Error {
  constructor(readonly response: Response) {
    super();
  }
}

async function getErrorMessage(response: Response) {
  const text = await response.text();
  return text ? `${response.status}: ${text}` : `${response.status}`;
}

class OwnersApi {
  constructor(readonly restApi: RestPluginApi) {}

  async getAccount(): Promise<AccountDetailInfo | undefined> {
    const loggedIn = await this.restApi.getLoggedIn();
    if (!loggedIn) return undefined;
    return await this.restApi.getAccount();
  }

  /**
   * Returns the list of owners associated to each file that needs a review or were approved,
   * and, for each owner, its current labels and votes.
   *
   * @doc
   * https://gerrit.googlesource.com/plugins/owners/+/refs/heads/master/owners/src/main/resources/Documentation/rest-api.md
   */
  getFilesOwners(
    repoName: RepoName,
    changeId: NumericChangeId,
    revision: String
  ): Promise<FilesOwners> {
    return this.get(
      `/changes/${encodeURIComponent(
        repoName
      )}~${changeId}/revisions/${revision}/owners~files-owners`
    ) as Promise<FilesOwners>;
  }

  private async get(url: string): Promise<unknown> {
    const errFn = (response?: Response | null, error?: Error) => {
      if (error) throw error;
      if (response) throw new ResponseError(response);
      throw new Error('Generic REST API error');
    };
    try {
      return await this.restApi.send(HttpMethod.GET, url, undefined, errFn);
    } catch (err) {
      if (err instanceof ResponseError && err.response.status === 409) {
        getErrorMessage(err.response).then(msg => {
          // TODO handle 409 in UI - show banner with error
          console.error(`Plugin configuration errot: ${msg}`);
        });
      }
      throw err;
    }
  }
}

/**
 * Calls to REST API can be safely cached cuz:
 * 1. Gerrit doesn't update the existing change object but obtains and passes new instead
 * 2. `OwnersService.getOwnersService` guarantees that whenever new change object is retrieved the new service instance is created (and cache is created with it)
 */
class OwnersApiCache {
  private loggedInUser?: Promise<User>;

  private filesOwners?: Promise<FilesOwners | undefined>;

  constructor(
    private readonly api: OwnersApi,
    private readonly change: ChangeInfo
  ) {}

  getLoggedInUser(): Promise<User> {
    if (this.loggedInUser === undefined) {
      this.loggedInUser = this.getLoggedInUserImpl();
    }
    return this.loggedInUser;
  }

  getFilesOwners(): Promise<FilesOwners | undefined> {
    if (this.filesOwners === undefined) {
      this.filesOwners = this.getFilesOwnersImpl();
    }
    return this.filesOwners;
  }

  private async getLoggedInUserImpl(): Promise<User> {
    const account = await this.api.getAccount();
    if (!account) {
      return {role: UserRole.ANONYMOUS} as unknown as User;
    }
    const role =
      this.change.owner._account_id === account._account_id
        ? UserRole.CHANGE_OWNER
        : UserRole.OTHER;
    return {account, role} as unknown as User;
  }

  private async getFilesOwnersImpl(): Promise<FilesOwners | undefined> {
    return this.api.getFilesOwners(
      this.change.project,
      this.change._number,
      this.change.current_revision ?? 'current'
    );
  }
}

let service: OwnersService | undefined;

export class OwnersService {
  private apiCache: OwnersApiCache;

  constructor(readonly restApi: RestPluginApi, readonly change: ChangeInfo) {
    this.apiCache = new OwnersApiCache(new OwnersApi(restApi), change);
  }

  getLoggedInUser(): Promise<User> {
    return this.apiCache.getLoggedInUser();
  }

  getFilesOwners(): Promise<FilesOwners | undefined> {
    return this.apiCache.getFilesOwners();
  }

  static getOwnersService(restApi: RestPluginApi, change: ChangeInfo) {
    if (!service || service.change !== change) {
      service = new OwnersService(restApi, change);
    }
    return service;
  }
}
