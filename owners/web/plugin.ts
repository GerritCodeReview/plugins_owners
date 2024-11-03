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
import '@gerritcodereview/typescript-api/gerrit';
import {
  FILES_OWNERS_COLUMN_CONTENT,
  FILES_OWNERS_COLUMN_HEADER,
  FilesColumnContent,
  FilesColumnHeader,
} from './gr-files';
import {
  OWNED_FILES_TAB_CONTENT,
  OWNED_FILES_TAB_HEADER,
  OwnedFilesTabContent,
  OwnedFilesTabHeader,
} from './gr-owned-files';

window.Gerrit.install(plugin => {
  const restApi = plugin.restApi();

  plugin
    .registerDynamicCustomComponent(
      'change-view-file-list-header-prepend',
      FILES_OWNERS_COLUMN_HEADER
    )
    .onAttached(view => {
      (view as unknown as FilesColumnHeader).restApi = restApi;
    });
  plugin
    .registerDynamicCustomComponent(
      'change-view-file-list-content-prepend',
      FILES_OWNERS_COLUMN_CONTENT
    )
    .onAttached(view => {
      (view as unknown as FilesColumnContent).restApi = restApi;
    });
  plugin
    .registerDynamicCustomComponent(
      'change-view-tab-header',
      OWNED_FILES_TAB_HEADER
    )
    .onAttached(view => {
      (view as unknown as OwnedFilesTabHeader).restApi = restApi;
    });
  plugin
    .registerDynamicCustomComponent(
      'change-view-tab-content',
      OWNED_FILES_TAB_CONTENT
    )
    .onAttached(view => {
      (view as unknown as OwnedFilesTabContent).restApi = restApi;
    });
});
