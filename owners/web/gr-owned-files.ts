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
  css,
  html,
  LitElement,
  PropertyValues,
  nothing,
  CSSResult,
  unsafeCSS,
} from 'lit';
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

export enum FileStatus {
  NEEDS_APPROVAL = 'missing',
  APPROVED = 'approved',
}

const STATUS_ICON = {
  [FileStatus.NEEDS_APPROVAL]: 'schedule',
  [FileStatus.APPROVED]: 'check',
};

interface OwnedFileInfo {
  status: FileStatus;
  file: string;
}

export interface OwnedFilesInfo {
  ownedFiles: OwnedFileInfo[];
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
    const template = document.querySelector(
      'dom-module#shared-styles > template'
    ) as HTMLTemplateElement;
    const sharedStyles = css`
      ${unsafeCSS(template?.content?.querySelector('style')?.textContent)}
    `;
    return [sharedStyles, window?.Gerrit?.styles.font as CSSResult];
  }

  private computeOwnedFiles() {
    this.ownedFilesInfo = ownedFiles(this.user?.account, this.filesOwners);
  }
}

export const OWNED_FILES_TAB_HEADER = 'owned-files-tab-header';
@customElement(OWNED_FILES_TAB_HEADER)
export class OwnedFilesTabHeader extends OwnedFilesCommon {
  @property({type: String, reflect: true, attribute: 'files-status'})
  filesStatus?: FileStatus;

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
        this.ownedFilesInfo.numberOfPending > 0
          ? [FileStatus.NEEDS_APPROVAL, this.ownedFilesInfo.numberOfPending]
          : [FileStatus.APPROVED, this.ownedFilesInfo.numberOfApproved];
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
    filesStatus: FileStatus,
    filesApproved: number,
    filesPending: number
  ): [string, string] {
    const pendingInfo =
      filesPending > 0
        ? `Missing approval for ${filesPending} file${
            filesPending > 1 ? 's' : ''
          }`
        : '';
    const approvedInfo =
      filesApproved > 0
        ? `${filesApproved} file${
            filesApproved > 1 ? 's' : ''
          } already approved.`
        : '';
    const info = `${
      FileStatus.APPROVED === filesStatus
        ? approvedInfo
        : `${pendingInfo}${filesApproved > 0 ? ` and ${approvedInfo}` : '.'}`
    }`;
    const summary = `${
      FileStatus.APPROVED === filesStatus ? 'Approved' : 'Missing'
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
  ownedFiles?: OwnedFileInfo[];

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
          padding-left: var(--spacing-m);
          background-color: var(--background-color-secondary);
        }
        .file-row {
          padding-left: var(--spacing-m);
          cursor: pointer;
        }
        .file-row:hover {
          background-color: var(--hover-background-color);
        }
        .file-row.selected {
          background-color: var(--selection-background-color);
        }
        .status {
          padding: inherit;
        }
        .path {
          padding: inherit;
          cursor: pointer;
          flex: 1;
          /* Wrap it into multiple lines if too long. */
          white-space: normal;
          word-break: break-word;
        }
        gr-icon.status.approved {
          color: var(--positive-green-text-color);
        }
        gr-icon.status.missing {
          color: #ffa62f;
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
        <div class="status" role="columnheader">Status</div>
        <div class="path" role="columnheader">File</div>
      </div>
    `;
  }

  private renderOwnedFileRow(ownedFile: OwnedFileInfo, index: number) {
    return html`
      <div
        class="file-row row"
        tabindex="-1"
        role="row"
        aria-label=${ownedFile.file}
      >
        ${this.renderFileStatus(ownedFile.status)}
        ${this.renderFilePath(ownedFile.file, index)}
      </div>
    `;
  }

  private renderFileStatus(status: FileStatus) {
    const icon = STATUS_ICON[status];
    return html`
      <span class="status" role="gridcell">
        <gr-icon
          class="status ${status}"
          icon=${icon}
          aria-hidden="true"
        ></gr-icon>
      </span>
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
            ${this.renderStyledPath(file, previousFile?.file)}
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
  ownedFiles?: OwnedFileInfo[]
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
  fileStatus: FileStatus,
  emailWithoutDomain?: string
): OwnedFileInfo[] {
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
      ownedFiles.push({file, status: fileStatus} as OwnedFileInfo);
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
    FileStatus.NEEDS_APPROVAL,
    emailWithoutDomain
  );
  const approvedFiles = collectOwnedFiles(
    owner,
    groupPrefix,
    filesOwners.files_approved,
    FileStatus.APPROVED,
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
