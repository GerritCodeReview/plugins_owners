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

import {AccountInfo} from '@gerritcodereview/typescript-api/rest-api';

/**
 * A provider for a value. Copied from Gerrit core.
 */
export type Provider<T> = () => T;

/**
 * Partial Gerrit's `AccountsModel` interface that defines functions needed in the owners plugin
 */
export interface AccountsModel {
  fillDetails(account: AccountInfo): AccountInfo;
}

/**
 * Parital Gerrit's `GrAccountLabel` interface that provides access to the `AccountsModel`
 */
export interface GrAccountLabel extends Element {
  getAccountsModel: Provider<AccountsModel>;
}

/**
 * Partial Gerrit's `SpecialFilePath` enum
 */
export enum SpecialFilePath {
  COMMIT_MESSAGE = '/COMMIT_MSG',
  MERGE_LIST = '/MERGE_LIST',
}

/**
 * Partial Gerrit's `Window` interface defintion so that `getBaseUrl` function can work.
 */
declare global {
  interface Window {
    CANONICAL_PATH?: string;
  }
}
