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

import {html, LitElement, PropertyValues, nothing, CSSResult} from 'lit';
import {OwnersMixin} from './owners-mixin';
import {customElement} from 'lit/decorators';
import {
  AccountInfo,
  ChangeInfo,
  ChangeStatus,
} from '@gerritcodereview/typescript-api/rest-api';
import {User, UserRole} from './owners-model';
import {isOwner, OwnedFiles, OWNERS_SUBMIT_REQUIREMENT} from './owners-service';

const common = OwnersMixin(LitElement);

class OwnedFilesCommon extends common {
  protected ownedFiles?: string[];

  protected override willUpdate(changedProperties: PropertyValues): void {
    super.willUpdate(changedProperties);
    this.computeOwnedFiles();

    this.hidden = shouldHide(
      this.change,
      this.allFilesApproved,
      this.user,
      this.ownedFiles
    );
  }

  protected static commonStyles(): CSSResult[] {
    return [window?.Gerrit?.styles.font as CSSResult];
  }

  private computeOwnedFiles() {
    this.ownedFiles = ownedFiles(this.user?.account, this.filesOwners?.files);
  }
}

export const OWNED_FILES_TAB_HEADER = 'owned-files-tab-header';
@customElement(OWNED_FILES_TAB_HEADER)
export class OwnedFilesTabHeader extends OwnedFilesCommon {
  static override get styles() {
    return [...OwnedFilesCommon.commonStyles()];
  }

  override render() {
    if (this.hidden) return nothing;
    return html`<div>Owned Files</div>`;
  }
}

export const OWNED_FILES_TAB_CONTENT = 'owned-files-tab-content';
@customElement(OWNED_FILES_TAB_CONTENT)
export class OwnedFilesTabContent extends OwnedFilesCommon {
  static override get styles() {
    return [...OwnedFilesCommon.commonStyles()];
  }

  override render() {
    if (this.hidden || !this.ownedFiles) return nothing;
    return html`<div>
      ${this.ownedFiles.map(ownedFile => html`<div>${ownedFile}</div>`)}
    </div>`;
  }
}

export function shouldHide(
  change?: ChangeInfo,
  allFilesApproved?: boolean,
  user?: User,
  ownedFiles?: string[]
) {
  // don't show owned files when no change or change is merged
  if (change === undefined) {
    return true;
  }
  if (
    change.status === ChangeStatus.ABANDONED ||
    change.status === ChangeStatus.MERGED
  ) {
    return true;
  }

  // show owned files if user owns anything
  if (
    !allFilesApproved &&
    change.submit_requirements &&
    change.submit_requirements.find(r => r.name === OWNERS_SUBMIT_REQUIREMENT)
  ) {
    return (
      !user ||
      user.role === UserRole.ANONYMOUS ||
      (ownedFiles ?? []).length === 0
    );
  }
  return true;
}

export function ownedFiles(
  owner?: AccountInfo,
  files?: OwnedFiles
): string[] | undefined {
  if (!owner || !files) {
    return;
  }

  const ownedFiles = [];
  for (const file of Object.keys(files)) {
    if (
      files[file].find(
        fileOwner => isOwner(fileOwner) && fileOwner.id === owner._account_id
      )
    ) {
      ownedFiles.push(file);
    }
  }

  return ownedFiles;
}
