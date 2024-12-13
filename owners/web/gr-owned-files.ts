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

import {css, html, LitElement, PropertyValues, nothing, CSSResult} from 'lit';
import {ifDefined} from 'lit/directives/if-defined.js';
import {OwnersMixin} from './owners-mixin';
import {customElement, property} from 'lit/decorators';
import {
  AccountInfo,
  ChangeInfo,
  ChangeStatus,
  EmailAddress,
  RevisionInfo,
  EDIT,
} from '@gerritcodereview/typescript-api/rest-api';
import {User, UserRole} from './owners-model';
import {
  FilesOwners,
  hasOwnersSubmitRequirement,
  isOwner,
  OwnedFiles,
} from './owners-service';
import {
  computeDisplayPath,
  diffFilePaths,
  encodeURL,
  getBaseUrl,
  truncatePath,
} from './utils';

const STATUS_CODE = {
  MISSING: 'missing',
  APPROVED: 'approved',
};

const STATUS_ICON = {
  [STATUS_CODE.MISSING]: 'schedule',
  [STATUS_CODE.APPROVED]: 'check',
};

export interface OwnedFilesInfo {
  ownedFiles: string[];
  numberOfPending: number;
  numberOfApproved: number;
}

const common = OwnersMixin(LitElement);

class OwnedFilesCommon extends common {
  @property({type: Object})
  revision?: RevisionInfo;

  protected ownedFilesInfo?: OwnedFilesInfo;

  protected override willUpdate(changedProperties: PropertyValues): void {
    super.willUpdate(changedProperties);
    this.computeOwnedFiles();

    this.hidden = shouldHide(
      this.change,
      this.revision,
      this.user,
      this.ownedFilesInfo?.ownedFiles
    );
  }

  static commonStyles(): CSSResult[] {
    return [window?.Gerrit?.styles.font as CSSResult];
  }

  private computeOwnedFiles() {
    this.ownedFilesInfo = ownedFiles(this.user?.account, this.filesOwners);
  }
}

export const OWNED_FILES_TAB_HEADER = 'owned-files-tab-header';
@customElement(OWNED_FILES_TAB_HEADER)
export class OwnedFilesTabHeader extends OwnedFilesCommon {
  @property({type: String, reflect: true, attribute: 'files-status'})
  filesStatus?: string;

  private ownedFiles: number | undefined;

  static override get styles() {
    return [
      ...OwnedFilesCommon.commonStyles(),
      css`
        [hidden] {
          display: none;
        }
        gr-icon {
          padding: var(--spacing-xs) 0px;
          margin-left: 3px;
        }
        :host([files-status='approved']) gr-icon.status {
          color: var(--positive-green-text-color);
        }
        :host([files-status='missing']) gr-icon.status {
          color: #ffa62f;
        }
      `,
    ];
  }

  protected override willUpdate(changedProperties: PropertyValues): void {
    super.willUpdate(changedProperties);

    if (this.hidden || this.ownedFilesInfo === undefined) {
      this.filesStatus = undefined;
      this.ownedFiles = undefined;
    } else {
      [this.filesStatus, this.ownedFiles] =
        this.ownedFilesInfo.numberOfApproved > 0
          ? [STATUS_CODE.APPROVED, this.ownedFilesInfo.numberOfApproved]
          : [STATUS_CODE.MISSING, this.ownedFilesInfo.numberOfPending];
    }
  }

  override render() {
    // even if `nothing` is returned Gerrit still shows the pointer and allows
    // clicking at it, redirecting to the empty tab when done; traverse through
    // the shadowRoots down to the tab and disable/enable it when needed
    const tabParent = document
      .querySelector('#pg-app')
      ?.shadowRoot?.querySelector('#app-element')
      ?.shadowRoot?.querySelector('main > gr-change-view')
      ?.shadowRoot?.querySelector(
        '#tabs > paper-tab[data-name="change-view-tab-header-owners"]'
      );
    if (this.hidden) {
      if (tabParent && !tabParent.getAttribute('disabled')) {
        tabParent.setAttribute('disabled', 'disabled');
      }
      return nothing;
    }
    if (tabParent && tabParent.getAttribute('disabled')) {
      tabParent.removeAttribute('disabled');
    }

    if (
      this.ownedFilesInfo === undefined ||
      this.filesStatus === undefined ||
      this.ownedFiles === undefined
    ) {
      return html`<div>Owned Files</div>`;
    }

    const icon = STATUS_ICON[this.filesStatus];
    const [info, summary] = this.buildInfoAndSummary(
      this.filesStatus,
      this.ownedFilesInfo.numberOfApproved,
      this.ownedFilesInfo.numberOfPending
    );
    return html` <div>
      Owned Files
      <gr-tooltip-content
        title=${info}
        aria-label=${summary}
        aria-description=${info}
        has-tooltip
      >
        <gr-icon
          id="owned-files-status"
          class="status"
          icon=${icon}
          aria-hidden="true"
        ></gr-icon>
      </gr-tooltip-content>
    </div>`;
  }

  private buildInfoAndSummary(
    filesStatus: string,
    filesApproved: number,
    filesPending: number
  ): [string, string] {
    const count = filesApproved + filesPending;
    const plural = count === 1 ? '' : 's';
    const info = `${
      STATUS_CODE.APPROVED === filesStatus
        ? `${count} file${plural} already approved.`
        : `Missing approval for ${count} file${plural}.`
    }`;
    const summary = `${
      STATUS_CODE.APPROVED === filesStatus ? 'Approved' : 'Missing'
    }`;
    return [info, summary];
  }
}

@customElement('gr-owned-files-list')
export class GrOwnedFilesList extends LitElement {
  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Object})
  revision?: RevisionInfo;

  @property({type: Array})
  ownedFiles?: string[];

  static override get styles() {
    return [
      ...OwnedFilesCommon.commonStyles(),
      css`
        :host {
          display: block;
        }
        .row {
          align-items: center;
          border-top: 1px solid var(--border-color);
          display: flex;
          min-height: calc(var(--line-height-normal) + 2 * var(--spacing-s));
          padding: var(--spacing-xs) var(--spacing-l);
        }
        .header-row {
          background-color: var(--background-color-secondary);
        }
        .file-row {
          cursor: pointer;
        }
        .file-row:hover {
          background-color: var(--hover-background-color);
        }
        .file-row.selected {
          background-color: var(--selection-background-color);
        }
        .path {
          cursor: pointer;
          flex: 1;
          /* Wrap it into multiple lines if too long. */
          white-space: normal;
          word-break: break-word;
        }
        .matchingFilePath {
          color: var(--deemphasized-text-color);
        }
        .newFilePath {
          color: var(--primary-text-color);
        }
        .fileName {
          color: var(--link-color);
        }
        .truncatedFileName {
          display: none;
        }
        .row:focus {
          outline: none;
        }
        .row:hover {
          opacity: 100;
        }
        .pathLink {
          display: inline-block;
          margin: -2px 0;
          padding: var(--spacing-s) 0;
          text-decoration: none;
        }
        .pathLink:hover span.fullFileName,
        .pathLink:hover span.truncatedFileName {
          text-decoration: underline;
        }
      `,
    ];
  }

  override render() {
    if (!this.change || !this.revision || !this.ownedFiles) return nothing;

    return html`
      <div id="container" role="grid" aria-label="Owned files list">
        ${this.renderOwnedFilesHeaderRow()}
        ${this.ownedFiles?.map((ownedFile, index) =>
          this.renderOwnedFileRow(ownedFile, index)
        )}
      </div>
    `;
  }

  private renderOwnedFilesHeaderRow() {
    return html`
      <div class="header-row row" role="row">
        <div class="path" role="columnheader">File</div>
      </div>
    `;
  }

  private renderOwnedFileRow(ownedFile: string, index: number) {
    return html`
      <div
        class="file-row row"
        tabindex="-1"
        role="row"
        aria-label=${ownedFile}
      >
        ${this.renderFilePath(ownedFile, index)}
      </div>
    `;
  }

  private renderFilePath(file: string, index: number) {
    const displayPath = computeDisplayPath(file);
    const previousFile = (this.ownedFiles ?? [])[index - 1];
    return html`
      <span class="path" role="gridcell">
        <a
          class="pathLink"
          href=${ifDefined(computeDiffUrl(file, this.change, this.revision))}
        >
          <span title=${displayPath} class="fullFileName">
            ${this.renderStyledPath(file, previousFile)}
          </span>
          <span title=${displayPath} class="truncatedFileName">
            ${truncatePath(displayPath)}
          </span>
        </a>
      </span>
    `;
  }

  private renderStyledPath(filePath: string, previousFilePath?: string) {
    const {matchingFolders, newFolders, fileName} = diffFilePaths(
      filePath,
      previousFilePath
    );
    return [
      matchingFolders.length > 0
        ? html`<span class="matchingFilePath">${matchingFolders}</span>`
        : nothing,
      newFolders.length > 0
        ? html`<span class="newFilePath">${newFolders}</span>`
        : nothing,
      html`<span class="fileName">${fileName}</span>`,
    ];
  }
}

export const OWNED_FILES_TAB_CONTENT = 'owned-files-tab-content';
@customElement(OWNED_FILES_TAB_CONTENT)
export class OwnedFilesTabContent extends OwnedFilesCommon {
  static override get styles() {
    return [
      ...OwnedFilesCommon.commonStyles(),
      css`
        :host {
          display: block;
        }
      `,
    ];
  }

  override render() {
    if (this.hidden) return nothing;

    return html`
      <gr-owned-files-list
        id="ownedFilesList"
        .change=${this.change}
        .revision=${this.revision}
        .ownedFiles=${this.ownedFilesInfo?.ownedFiles}
      >
      </gr-owned-files-list>
    `;
  }
}

export function shouldHide(
  change?: ChangeInfo,
  revision?: RevisionInfo,
  user?: User,
  ownedFiles?: string[]
) {
  // don't show owned files when no change or change is abandoned/merged or being edited or viewing not current PS
  if (
    change === undefined ||
    change.status === ChangeStatus.ABANDONED ||
    change.status === ChangeStatus.MERGED ||
    revision === undefined ||
    revision._number === EDIT ||
    change.current_revision !== revision.commit?.commit
  ) {
    return true;
  }

  // show owned files if user owns anything
  if (hasOwnersSubmitRequirement(change)) {
    return (
      !user ||
      user.role === UserRole.ANONYMOUS ||
      (ownedFiles ?? []).length === 0
    );
  }
  return true;
}

function collectOwnedFiles(
  owner: AccountInfo,
  groupPrefix: string,
  files: OwnedFiles,
  emailWithoutDomain?: string
): string[] {
  const ownedFiles = [];
  for (const file of Object.keys(files ?? [])) {
    if (
      files[file].find(fileOwner => {
        if (isOwner(fileOwner)) {
          return fileOwner.id === owner._account_id;
        }

        return (
          !fileOwner.name?.startsWith(groupPrefix) &&
          (fileOwner.name === emailWithoutDomain ||
            fileOwner.name === owner.name)
        );
      })
    ) {
      ownedFiles.push(file);
    }
  }

  return ownedFiles;
}

export function ownedFiles(
  owner?: AccountInfo,
  filesOwners?: FilesOwners
): OwnedFilesInfo | undefined {
  if (
    !owner ||
    !filesOwners ||
    (!filesOwners.files && !filesOwners.files_approved)
  ) {
    return;
  }

  const groupPrefix = 'group/';
  const emailWithoutDomain = toEmailWithoutDomain(owner.email);
  const pendingFiles = collectOwnedFiles(
    owner,
    groupPrefix,
    filesOwners.files,
    emailWithoutDomain
  );
  const approvedFiles = collectOwnedFiles(
    owner,
    groupPrefix,
    filesOwners.files_approved,
    emailWithoutDomain
  );
  return {
    ownedFiles: [...pendingFiles, ...approvedFiles],
    numberOfPending: pendingFiles.length,
    numberOfApproved: approvedFiles.length,
  } as OwnedFilesInfo;
}

export function computeDiffUrl(
  file: string,
  change?: ChangeInfo,
  revision?: RevisionInfo
) {
  if (change === undefined || revision?._number === undefined) {
    return;
  }

  let repo = '';
  if (change.project) repo = `${encodeURL(change.project)}/+/`;

  // TODO it can be a range of patchsets but `PatchRange` is not passed to the `change-view-tab-content` :/ fix in Gerrit?
  const range = `/${revision._number}`;

  const path = `/${encodeURL(file)}`;

  return `${getBaseUrl()}/c/${repo}${change._number}${range}${path}`;
}

function toEmailWithoutDomain(email?: EmailAddress): string | undefined {
  const startDomainIndex = email?.indexOf('@');
  return startDomainIndex ? email?.substring(0, startDomainIndex) : undefined;
}
