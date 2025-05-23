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
import {customElement, property} from 'lit/decorators.js';
import {
  AccountInfo,
  ApprovalInfo,
  ChangeInfo,
  ChangeStatus,
  GroupInfo,
  LabelInfo,
  isDetailedLabelInfo,
  EmailAddress,
  ReviewerState,
} from '@gerritcodereview/typescript-api/rest-api';
import {
  FilesOwners,
  isOwner,
  OwnersLabels,
  hasOwnersSubmitRequirement,
} from './owners-service';
import {
  FileOwnership,
  FileStatus,
  OwnerOrGroupOwner,
  PatchRange,
  UserRole,
} from './owners-model';
import {query} from './utils';
import {GrAccountLabel} from './gerrit-model';
import {OwnersMixin} from './owners-mixin';

const STATUS_CODE = {
  MISSING: 'missing',
  APPROVED: 'approved',
};

const STATUS_ICON = {
  [STATUS_CODE.MISSING]: 'schedule',
  [STATUS_CODE.APPROVED]: 'check',
};

const FILE_STATUS = {
  [FileStatus.NEEDS_APPROVAL]: STATUS_CODE.MISSING,
  [FileStatus.APPROVED]: STATUS_CODE.APPROVED,
};

const HOVER_HEADING = {
  [STATUS_CODE.MISSING]: "Needs Owners' Approval",
  [STATUS_CODE.APPROVED]: 'Approved by Owners',
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
      this.filesOwners,
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
  groupOwner?: GroupInfo;

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
    if (!this.owner && !this.groupOwner) {
      return nothing;
    }

    const isAccountOwner = this.owner !== undefined;
    const ownerLabel = isAccountOwner
      ? this.owner?._account_id
        ? html`<gr-account-label .account=${this.owner}></gr-account-label>`
        : html`<span>${this.owner?.display_name}</span>`
      : html`<span>Group: ${this.groupOwner?.name}</span>`;

    const voteChip =
      isAccountOwner && this.approval
        ? html` <gr-vote-chip
            .vote=${this.approval}
            .label=${this.info}
          ></gr-vote-chip>`
        : nothing;

    // allow user to copy what is available:
    // * email or name (if available) for AccountInfo owner type
    // * group name for GroupInfo owner type
    const [copyText, copyTooltip] = isAccountOwner
      ? this.email || this.owner?.display_name
        ? [
            this.email ?? this.owner?.display_name,
            this.email ? 'email' : 'name',
          ]
        : [nothing, nothing]
      : [this.groupOwner?.name, 'group name'];
    const copy = copyText
      ? html` <gr-copy-clipboard
          .text=${copyText}
          hasTooltip
          hideinput
          buttonTitle="Copy owner's ${copyTooltip} to clipboard"
        ></gr-copy-clipboard>`
      : nothing;

    return html`
      <div class="container">${ownerLabel} ${voteChip} ${copy}</div>
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

  private owners?: OwnerOrGroupOwner[];

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
        :host([file-status='approved']) gr-icon.status {
          color: var(--positive-green-text-color);
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
    const heading = HOVER_HEADING[this.fileStatus ?? STATUS_CODE.MISSING];
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
              <span>${heading}</span>
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
                    .owner=${owner.owner}
                    .groupOwner=${owner.groupOwner}
                    .approval=${approval}
                    .info=${info}
                    .email=${owner.owner ? owner.owner.email : nothing}
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
    const fileOwnership = getFileOwnership(this.path, this.filesOwners);
    if (!fileOwnership || fileOwnership.fileStatus === FileStatus.NOT_OWNED) {
      this.fileStatus = undefined;
      this.owners = undefined;
      return;
    }

    this.fileStatus = FILE_STATUS[fileOwnership.fileStatus];
    const accounts = getChangeAccounts(this.change);

    this.owners = (fileOwnership.owners ?? []).map(owner => {
      if (isOwner(owner)) {
        return {
          owner:
            accounts.byAccountId.get(owner.id) ??
            ({_account_id: owner.id} as unknown as AccountInfo),
        } as unknown as OwnerOrGroupOwner;
      }

      const groupPrefix = 'group/';
      if (owner.name.startsWith(groupPrefix)) {
        return {
          groupOwner: {
            name: owner.name.substring(groupPrefix.length),
          } as unknown as GroupInfo,
        } as unknown as OwnerOrGroupOwner;
      }

      // when `owners.expandGroups = false` then neither group nor account
      // will be expanded therefore try to match account with change's available
      // accounts through email without domain or by full name
      // finally construct `AccountInfo` just with a `name` property
      const accountOwner =
        accounts.byEmailWithoutDomain.get(owner.name) ??
        accounts.byFullName.get(owner.name) ??
        ({display_name: owner.name} as unknown as AccountInfo);
      return {owner: accountOwner} as unknown as OwnerOrGroupOwner;
    });
  }
}

export function shouldHide(
  change?: ChangeInfo,
  patchRange?: PatchRange,
  filesOwners?: FilesOwners,
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
    hasOwnersSubmitRequirement(change) &&
    filesOwners &&
    (filesOwners.files || filesOwners.files_approved)
  ) {
    return !userRole || userRole === UserRole.ANONYMOUS;
  }
  return true;
}

export function getFileOwnership(
  path?: string,
  filesOwners?: FilesOwners
): FileOwnership | undefined {
  if (path === undefined || filesOwners === undefined) {
    return undefined;
  }

  const fileOwners = (filesOwners.files ?? {})[path];
  const fileApprovers = (filesOwners.files_approved ?? {})[path];
  if (fileApprovers) {
    return {
      fileStatus: FileStatus.APPROVED,
      owners: fileApprovers,
    };
  } else if (fileOwners) {
    return {
      fileStatus: FileStatus.NEEDS_APPROVAL,
      owners: fileOwners,
    };
  } else {
    return {fileStatus: FileStatus.NOT_OWNED};
  }
}

export function computeApprovalAndInfo(
  fileOwner: OwnerOrGroupOwner,
  labels: OwnersLabels,
  change?: ChangeInfo
): [ApprovalInfo, LabelInfo] | undefined {
  if (!change?.labels || !fileOwner?.owner) {
    return;
  }
  const accountId = fileOwner.owner._account_id;
  const ownersLabel = labels[`${accountId}`];
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

    const approval = info.all?.filter(x => x._account_id === accountId)[0];
    return approval ? [approval, info] : undefined;
  }

  return;
}

export interface ChangeAccounts {
  byAccountId: Map<number, AccountInfo>;
  byEmailWithoutDomain: Map<string, AccountInfo>;
  byFullName: Map<string, AccountInfo>;
}

export function getChangeAccounts(change?: ChangeInfo): ChangeAccounts {
  const accounts = {
    byAccountId: new Map(),
    byEmailWithoutDomain: new Map(),
    byFullName: new Map(),
  } as unknown as ChangeAccounts;

  if (!change) {
    return accounts;
  }

  [
    change.owner,
    ...(change.submitter ? [change.submitter] : []),
    ...(change.reviewers[ReviewerState.REVIEWER] ?? []),
    ...(change.reviewers[ReviewerState.CC] ?? []),
  ].forEach(account => {
    if (account._account_id) {
      accounts.byAccountId.set(account._account_id, account);
    }

    if (account.email && account.email.indexOf('@') > 0) {
      accounts.byEmailWithoutDomain.set(
        account.email.substring(
          0,
          account.email.indexOf('@')
        ) as unknown as string,
        account
      );
    }

    if (account.name) {
      accounts.byFullName.set(account.name, account);
    }
  });
  return accounts;
}
