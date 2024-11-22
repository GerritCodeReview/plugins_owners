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

import {BehaviorSubject, Observable} from 'rxjs';
import {
  AccountInfo,
  BasePatchSetNum,
  ChangeInfo,
  GroupInfo,
  RevisionPatchSetNum,
} from '@gerritcodereview/typescript-api/rest-api';
import {FileOwner, FilesOwners, OwnersService} from './owners-service';
import {deepEqual} from './utils';

export interface PatchRange {
  patchNum: RevisionPatchSetNum;
  basePatchNum: BasePatchSetNum;
}

export enum UserRole {
  ANONYMOUS = 'ANONYMOUS',
  CHANGE_OWNER = 'CHANGE_OWNER',
  OTHER = 'OTHER',
}

export interface User {
  account?: AccountInfo;
  role: UserRole;
}

export interface OwnersState {
  user?: User;
  allFilesApproved?: boolean;
  filesOwners?: FilesOwners;
}

export enum FileStatus {
  NEEDS_APPROVAL = 'NEEDS_APPROVAL',
  APPROVED = 'APPROVED',
  NOT_OWNED = 'NOT_OWNED',
}

export interface FileOwnership {
  fileStatus: FileStatus;
  owners?: FileOwner[];
}

export interface OwnerOrGroupOwner {
  owner?: AccountInfo;
  groupOwner?: GroupInfo;
}

let ownersModel: OwnersModel | undefined;

export class OwnersModel extends EventTarget {
  private subject$: BehaviorSubject<OwnersState> = new BehaviorSubject(
    {} as OwnersState
  );

  public state$: Observable<OwnersState> = this.subject$.asObservable();

  constructor(readonly change: ChangeInfo) {
    super();
  }

  get state() {
    return this.subject$.getValue();
  }

  private setState(state: OwnersState) {
    this.subject$.next(Object.freeze(state));
  }

  setUser(user: User) {
    const current = this.subject$.getValue();
    if (current.user === user) return;
    this.setState({...current, user});
  }

  setAllFilesApproved(allFilesApproved: boolean | undefined) {
    const current = this.subject$.getValue();
    if (current.allFilesApproved === allFilesApproved) return;
    this.setState({...current, allFilesApproved});
  }

  setFilesOwners(filesOwners: FilesOwners | undefined) {
    const current = this.subject$.getValue();
    if (deepEqual(current.filesOwners, filesOwners)) return;
    this.setState({...current, filesOwners});
  }

  static getModel(change: ChangeInfo) {
    if (!ownersModel || ownersModel.change !== change) {
      ownersModel = new OwnersModel(change);
    }
    return ownersModel;
  }
}

export class ModelLoader {
  constructor(
    private readonly service: OwnersService,
    private readonly model: OwnersModel
  ) {}

  async loadUser() {
    await this._loadProperty(
      'user',
      () => this.service.getLoggedInUser(),
      value => this.model.setUser(value)
    );
  }

  async loadAllFilesApproved() {
    await this._loadProperty(
      'allFilesApproved',
      () => this.service.getAllFilesApproved(),
      value => this.model.setAllFilesApproved(value)
    );
  }

  async loadFilesOwners() {
    await this._loadProperty(
      'filesOwners',
      () => this.service.getFilesOwners(),
      value => this.model.setFilesOwners(value)
    );
  }

  private async _loadProperty<K extends keyof OwnersState, T>(
    propertyName: K,
    propertyLoader: () => Promise<T>,
    propertySetter: (value: T) => void
  ) {
    if (this.model.state[propertyName] !== undefined) return;
    let newValue: T;
    try {
      newValue = await propertyLoader();
    } catch (e) {
      console.error(e);
      return;
    }
    // It is possible, that several requests is made in parallel.
    // Store only the first result and discard all other results.
    // (also, due to the CodeOwnersCacheApi all result must be identical)
    if (this.model.state[propertyName] !== undefined) return;
    propertySetter(newValue);
  }
}
