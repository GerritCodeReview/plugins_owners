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

import {Subscription} from 'rxjs';
import {css, html, LitElement, nothing, PropertyValues, CSSResult} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {
  AccountInfo,
  ChangeInfo,
  ChangeStatus,
} from '@gerritcodereview/typescript-api/rest-api';
import {
  FilesOwners,
  isOwner,
  OWNERS_SUBMIT_REQUIREMENT,
  OwnersService,
} from './owners-service';
import {RestPluginApi} from '@gerritcodereview/typescript-api/rest';
import {
  FileOwnership,
  FileStatus,
  ModelLoader,
  OwnersModel,
  PatchRange,
  UserRole,
} from './owners-model';

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

// Lit mixin definition as described in https://lit.dev/docs/composition/mixins/
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Constructor<T> = new (...args: any[]) => T;

interface CommonInterface extends LitElement {
  change?: ChangeInfo;
  patchRange?: PatchRange;
  restApi?: RestPluginApi;
  userRole?: UserRole;
  allFilesApproved?: boolean;
  filesOwners?: FilesOwners;

  onModelUpdate(): void;
}

const CommonMixin = <T extends Constructor<LitElement>>(superClass: T) => {
  class Mixin extends superClass {
    @property({type: Object})
    change?: ChangeInfo;

    @property({type: Object})
    patchRange?: PatchRange;

    @property({type: Object})
    restApi?: RestPluginApi;

    @state()
    userRole?: UserRole;

    @state()
    allFilesApproved?: boolean;

    @state()
    filesOwners?: FilesOwners;

    private _model?: OwnersModel;

    modelLoader?: ModelLoader;

    private subscriptions: Array<Subscription> = [];

    get model() {
      return this._model;
    }

    set model(model: OwnersModel | undefined) {
      if (this._model === model) return;
      for (const s of this.subscriptions) {
        s.unsubscribe();
      }
      this.subscriptions = [];
      this._model = model;
      if (!model) return;

      this.subscriptions.push(
        model.state$.subscribe(s => {
          this.userRole = s.userRole;
        })
      );

      this.subscriptions.push(
        model.state$.subscribe(s => {
          this.allFilesApproved = s.allFilesApproved;
        })
      );

      this.subscriptions.push(
        model.state$.subscribe(s => {
          this.filesOwners = s.filesOwners;
        })
      );

      this.onModelUpdate();
    }

    protected override willUpdate(changedProperties: PropertyValues): void {
      super.willUpdate(changedProperties);

      if (changedProperties.has('change') || changedProperties.has('restApi')) {
        if (!this.restApi || !this.change) {
          this.model = undefined;
          this.modelLoader = undefined;
          return;
        }
        const service = OwnersService.getOwnersService(
          this.restApi,
          this.change
        );
        const model = OwnersModel.getModel(this.change);
        this.modelLoader = new ModelLoader(service, model);
        this.model = model;
      }

      this.hidden = shouldHide(
        this.change,
        this.patchRange,
        this.allFilesApproved,
        this.userRole
      );
    }

    protected onModelUpdate() {
      this.modelLoader?.loadUserRole();
      this.modelLoader?.loadAllFilesApproved();
      this.modelLoader?.loadFilesOwners();
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    constructor(...args: any[]) {
      super(...args);
    }
  }

  return Mixin as unknown as T & Constructor<CommonInterface>;
};

const common = CommonMixin(LitElement);

export const FILE_OWNERS_COLUMN_HEADER = 'file-owners-column-header';
@customElement(FILE_OWNERS_COLUMN_HEADER)
export class FileOwnersColumnHeader extends common {
  static override get styles() {
    return [
      window?.Gerrit?.styles.font as CSSResult,
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

export const FILE_OWNERS_COLUMN_CONTENT = 'file-owners-column-content';
@customElement(FILE_OWNERS_COLUMN_CONTENT)
export class FileOwnersColumnContent extends common {
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
      window?.Gerrit?.styles.font as CSSResult,
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
            ${splicedOwners.map(
              (owner, idx) => html`
                <div
                  class="row ${showEllipsis || idx + 1 < splicedOwners.length
                    ? 'notLast'
                    : ''}"
                >
                  <gr-account-label .account=${owner}></gr-account-label>
                </div>
              `
            )}
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
    // TODO for the time being filter out or group owners - to be decided what/how to display them
    this.owners = (fileOwnership.owners ?? [])
      .filter(isOwner)
      .map(o => ({_account_id: o.id} as unknown as AccountInfo));
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
