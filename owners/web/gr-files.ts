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

import {css, html, LitElement, nothing, PropertyValues, CSSResult} from 'lit';
import {customElement, property} from 'lit/decorators';
import {
  AccountInfo,
  ApprovalInfo,
  ChangeInfo,
  ChangeStatus,
  LabelInfo,
  isDetailedLabelInfo,
  EmailAddress,
  ReviewerState,
} from '@gerritcodereview/typescript-api/rest-api';
import {
  FilesOwners,
  isOwner,
  OwnersLabels,
  OWNERS_SUBMIT_REQUIREMENT,
} from './owners-service';
import {FileOwnership, FileStatus, PatchRange, UserRole} from './owners-model';
import {query} from './utils';
import {GrAccountLabel} from './gerrit-model';
import {OwnersMixin} from './owners-mixin';

const STATUS_CODE = {
  MISSING: 'missing',
};

const STATUS_ICON = {
  [STATUS_CODE.MISSING]: 'schedule',
};

const FILE_STATUS = {
  [FileStatus.NEEDS_APPROVAL]: STATUS_CODE.MISSING,
};

const DISPLAY_OWNERS_FOR_FILE_LIMIT = 5;
const LIMITED_FILES_OWNERS_TOOLTIP = `File owners limited to first ${DISPLAY_OWNERS_FOR_FILE_LIMIT} accounts.`;

const common = OwnersMixin(LitElement);

class FilesCommon extends common {
  protected override willUpdate(changedProperties: PropertyValues): void {
    super.willUpdate(changedProperties);

    this.hidden = shouldHide(
      this.change,
      this.patchRange,
      this.allFilesApproved,
      this.user?.role
    );
  }

  protected static commonStyles(): CSSResult[] {
    return [window?.Gerrit?.styles.font as CSSResult];
  }
}

export const FILES_OWNERS_COLUMN_HEADER = 'files-owners-column-header';
@customElement(FILES_OWNERS_COLUMN_HEADER)
export class FilesColumnHeader extends FilesCommon {
  static override get styles() {
    return [
      ...FilesCommon.commonStyles(),
      css`
        :host() {
          display: block;
          padding-right: var(--spacing-m);
          width: 4em;
        }
        :host[hidden] {
          display: none;
        }
      `,
    ];
  }

  override render() {
    if (this.hidden) return nothing;
    return html`<div>Status</div>`;
  }
}

/**
 * It has to be part of this file as components defined in dedicated files are not visible
 */
@customElement('gr-owner')
export class GrOwner extends LitElement {
  @property({type: Object})
  owner?: AccountInfo;

  @property({type: Object})
  approval?: ApprovalInfo;

  @property({type: Object})
  info?: LabelInfo;

  @property({type: String})
  email?: EmailAddress;

  static override get styles() {
    return [
      css`
        .container {
          display: flex;
        }
        gr-vote-chip {
          margin-left: 5px;
          --gr-vote-chip-width: 14px;
          --gr-vote-chip-height: 14px;
        }
        gr-tooltip-content {
          display: inline-block;
        }
      `,
    ];
  }

  override render() {
    if (!this.owner) {
      return nothing;
    }

    const voteChip = this.approval
      ? html` <gr-vote-chip
          slot="vote-chip"
          .vote=${this.approval}
          .label=${this.info}
          circle-shape
        ></gr-vote-chip>`
      : nothing;

    const copyEmail = this.email
      ? html` <gr-copy-clipboard
          .text=${this.email}
          hasTooltip
          hideinput
          buttonTitle=${"Copy owner's email to clipboard"}
        ></gr-copy-clipboard>`
      : nothing;

    return html`
      <div class="container">
        <gr-account-label .account=${this.owner}></gr-account-label>
        ${voteChip} ${copyEmail}
      </div>
    `;
  }

  override async updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);

    if (changedProperties.has('owner')) {
      if (this.owner && !this.email) {
        const accountLabel = query<GrAccountLabel>(this, 'gr-account-label');
        if (!accountLabel) {
          return;
        }

        const updateOwner = await accountLabel
          ?.getAccountsModel()
          ?.fillDetails(this.owner);
        this.email = updateOwner?.email;
      }
    }
  }
}

export const FILES_OWNERS_COLUMN_CONTENT = 'files-owners-column-content';
@customElement(FILES_OWNERS_COLUMN_CONTENT)
export class FilesColumnContent extends FilesCommon {
  @property({type: String})
  path?: string;

  @property({type: String})
  oldPath?: string;

  @property({type: String, reflect: true, attribute: 'file-status'})
  fileStatus?: string;

  private owners?: AccountInfo[];

  // taken from Gerrit's common-util.ts
  private uniqueId = Math.random().toString(36).substring(2);

  static override get styles() {
    return [
      ...FilesCommon.commonStyles(),
      css`
        :host {
          display: flex;
          padding-right: var(--spacing-m);
          width: 4em;
          text-align: center;
        }
        :host[hidden] {
          display: none;
        }
        gr-icon {
          padding: var(--spacing-xs) 0px;
          margin-left: 9px;
        }
        :host([file-status='missing']) gr-icon.status {
          color: #ffa62f;
        }
      `,
    ];
  }

  override render() {
    if (this.hidden || !this.fileStatus) {
      return nothing;
    }

    const icon = STATUS_ICON[this.fileStatus];
    return html`
      <gr-icon
        id="${this.pathId()}"
        class="status"
        icon=${icon}
        aria-hidden="true"
      ></gr-icon>
      ${this.renderFileOwners()}
    `;
  }

  private pathId(): string {
    return `path-${this.uniqueId}`;
  }

  private renderFileOwners() {
    const owners = this.owners ?? [];
    const splicedOwners = owners.splice(0, DISPLAY_OWNERS_FOR_FILE_LIMIT);
    const showEllipsis = owners.length > DISPLAY_OWNERS_FOR_FILE_LIMIT;
    // inlining <style> here is ugly but an alternative would be to copy the `HovercardMixin` from Gerrit and implement hoover from scratch
    return html`<gr-hovercard for="${this.pathId()}">
      <style>
        #file-owners-hoovercard {
          min-width: 256px;
          max-width: 256px;
          margin: -10px;
          padding: var(--spacing-xl) 0 var(--spacing-m) 0;
        }
        h3 {
          font: inherit;
        }
        .heading-3 {
          margin-left: -2px;
          font-family: var(--header-font-family);
          font-size: var(--font-size-h3);
          font-weight: var(--font-weight-h3);
          line-height: var(--line-height-h3);
        }
        .row {
          display: flex;
        }
        div.section {
          margin: 0 var(--spacing-xl) var(--spacing-m) var(--spacing-xl);
          display: flex;
          align-items: center;
        }
        div.sectionIcon {
          flex: 0 0 30px;
        }
        div.sectionIcon gr-icon {
          position: relative;
        }
        div.sectionContent .row {
          margin-left: 2px;
        }
        div.sectionContent .notLast {
          margin-bottom: 2px;
        }
        div.sectionContent .ellipsis {
          margin-left: 5px;
        }
      </style>
      <div id="file-owners-hoovercard">
        <div class="section">
          <div class="sectionIcon">
            <gr-icon class="status" icon="info" aria-hidden="true"></gr-icon>
          </div>
          <div class="sectionContent">
            <h3 class="name heading-3">
              <span>Needs Owners' Approval</span>
            </h3>
          </div>
        </div>
        <div class="section">
          <div class="sectionContent">
            ${splicedOwners.map((owner, idx) => {
              const [approval, info] =
                computeApprovalAndInfo(
                  owner,
                  this.filesOwners?.owners_labels ?? {},
                  this.change
                ) ?? [];
              return html`
                <div
                  class="row ${showEllipsis || idx + 1 < splicedOwners.length
                    ? 'notLast'
                    : ''}"
                >
                  <gr-owner
                    .owner=${owner}
                    .approval=${approval}
                    .info=${info}
                    .email=${owner.email}
                  ></gr-owner>
                </div>
              `;
            })}
            ${showEllipsis
              ? html`
                <gr-tooltip-content
                  title=${LIMITED_FILES_OWNERS_TOOLTIP}
                  aria-label="limited-file-onwers"
                  aria-description=${LIMITED_FILES_OWNERS_TOOLTIP}
                  has-tooltip
                >
                  <div class="row ellipsis">...</div>
                </gt-tooltip-content>`
              : nothing}
          </div>
        </div>
      </div>
    </gr-hovercard>`;
  }

  protected override willUpdate(changedProperties: PropertyValues): void {
    super.willUpdate(changedProperties);
    this.computeFileState();
  }

  private computeFileState(): void {
    const fileOwnership = getFileOwnership(
      this.path,
      this.allFilesApproved,
      this.filesOwners
    );
    if (
      !fileOwnership ||
      fileOwnership.fileStatus === FileStatus.NOT_OWNED_OR_APPROVED
    ) {
      this.fileStatus = undefined;
      this.owners = undefined;
      return;
    }

    this.fileStatus = FILE_STATUS[fileOwnership.fileStatus];
    const accounts = getChangeAccounts(this.change);

    // TODO for the time being filter out or group owners - to be decided what/how to display them
    this.owners = (fileOwnership.owners ?? [])
      .filter(isOwner)
      .map(
        o =>
          accounts.get(o.id) ?? ({_account_id: o.id} as unknown as AccountInfo)
      );
  }
}

export function shouldHide(
  change?: ChangeInfo,
  patchRange?: PatchRange,
  allFilesApproved?: boolean,
  userRole?: UserRole
): boolean {
  // don't show owners when no change or change is merged
  if (change === undefined || patchRange === undefined) {
    return true;
  }
  if (
    change.status === ChangeStatus.ABANDONED ||
    change.status === ChangeStatus.MERGED
  ) {
    return true;
  }

  // Note: in some special cases, patchNum is undefined on latest patchset
  // like after publishing the edit, still show for them
  // TODO: this should be fixed in Gerrit
  if (patchRange?.patchNum === undefined) return false;

  // only show if its latest patchset
  const latestPatchset = change.revisions![change.current_revision!];
  if (`${patchRange.patchNum}` !== `${latestPatchset._number}`) {
    return true;
  }

  // show owners when they apply to the change and for logged in user
  if (
    !allFilesApproved &&
    change.submit_requirements &&
    change.submit_requirements.find(r => r.name === OWNERS_SUBMIT_REQUIREMENT)
  ) {
    return !userRole || userRole === UserRole.ANONYMOUS;
  }
  return true;
}

export function getFileOwnership(
  path?: string,
  allFilesApproved?: boolean,
  filesOwners?: FilesOwners
): FileOwnership | undefined {
  if (path === undefined || filesOwners === undefined) {
    return undefined;
  }

  const fileOwners = (filesOwners.files ?? {})[path];
  return (allFilesApproved || !fileOwners
    ? {fileStatus: FileStatus.NOT_OWNED_OR_APPROVED}
    : {
        fileStatus: FileStatus.NEEDS_APPROVAL,
        owners: fileOwners,
      }) as unknown as FileOwnership;
}

export function computeApprovalAndInfo(
  fileOwner: AccountInfo,
  labels: OwnersLabels,
  change?: ChangeInfo
): [ApprovalInfo, LabelInfo] | undefined {
  if (!change?.labels) {
    return;
  }
  const ownersLabel = labels[`${fileOwner._account_id}`];
  if (!ownersLabel) {
    return;
  }

  for (const label of Object.keys(ownersLabel)) {
    const info = change.labels[label];
    if (!info || !isDetailedLabelInfo(info)) {
      return;
    }

    const vote = ownersLabel[label];
    if ((info.default_value && info.default_value === vote) || vote === 0) {
      // ignore default value
      return;
    }

    const approval = info.all?.filter(
      x => x._account_id === fileOwner._account_id
    )[0];
    return approval ? [approval, info] : undefined;
  }

  return;
}

export function getChangeAccounts(
  change?: ChangeInfo
): Map<number, AccountInfo> {
  const accounts = new Map();
  if (!change) {
    return accounts;
  }

  [
    change.owner,
    ...(change.submitter ? [change.submitter] : []),
    ...(change.reviewers[ReviewerState.REVIEWER] ?? []),
    ...(change.reviewers[ReviewerState.CC] ?? []),
  ].forEach(account => accounts.set(account._account_id, account));
  return accounts;
}
