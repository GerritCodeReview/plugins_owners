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
  ChangeStatus,
  NumericChangeId,
  RepoName,
  SubmitRequirementStatus,
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

export const OWNERS_SUBMIT_REQUIREMENT = 'Owner-Approval';

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
   * Returns the list of owners associated to each file that needs a review,
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

let service: OwnersService | undefined;

export class OwnersService {
  private api: OwnersApi;

  constructor(readonly restApi: RestPluginApi, readonly change: ChangeInfo) {
    this.api = new OwnersApi(restApi);
  }

  async getLoggedInUser(): Promise<User> {
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

  async getAllFilesApproved(): Promise<boolean | undefined> {
    if (!(await this.isLoggedIn())) {
      return Promise.resolve(undefined);
    }

    if (
      this.change?.status === ChangeStatus.ABANDONED ||
      this.change?.status === ChangeStatus.MERGED
    ) {
      return Promise.resolve(undefined);
    }

    const ownersSr = this.change?.submit_requirements?.find(
      r => r.name === OWNERS_SUBMIT_REQUIREMENT
    );
    if (!ownersSr) {
      return Promise.resolve(undefined);
    }

    return Promise.resolve(
      ownersSr.status === SubmitRequirementStatus.SATISFIED
    );
  }

  async getFilesOwners(): Promise<FilesOwners | undefined> {
    return this.api.getFilesOwners(
      this.change.project,
      this.change._number,
      this.change.current_revision ?? 'current'
    );
  }

  private async isLoggedIn(): Promise<boolean> {
    const user = await this.getLoggedInUser();
    return user && user.role !== UserRole.ANONYMOUS;
  }

  static getOwnersService(restApi: RestPluginApi, change: ChangeInfo) {
    if (!service || service.change !== change) {
      service = new OwnersService(restApi, change);
    }
    return service;
  }
}
