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
import {LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {
  ChangeInfo,
  ChangeStatus,
} from '@gerritcodereview/typescript-api/rest-api';
import {OwnersService} from './owners-service';
import {RestPluginApi} from '@gerritcodereview/typescript-api/rest';
import {ModelLoader, OwnersModel, PatchRange, UserRole} from './owners-model';

// Lit mixin definition as described in https://lit.dev/docs/composition/mixins/
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Constructor<T> = new (...args: any[]) => T;

interface CommonInterface {
  change?: ChangeInfo;
  patchRange?: PatchRange;
  restApi?: RestPluginApi;
  userRole?: UserRole;

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

      this.onModelUpdate();
    }

    protected override willUpdate(changedProperties: PropertyValues): void {
      super.willUpdate(changedProperties);

      if (changedProperties.has('change') || changedProperties.has('restApi')) {
        if (
          changedProperties.has('change') ||
          changedProperties.has('restApi')
        ) {
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
      }

      this.hidden = shouldHide(this.change, this.patchRange, this.userRole);
    }

    protected onModelUpdate() {
      this.modelLoader?.loadUserRole();
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
    return [];
  }

  override render() {
    console.log(
      `hidden: ${shouldHide(
        this.change,
        this.patchRange,
        this.userRole
      )}, userRole: ${this.userRole}`
    );
    return nothing;
  }
}

export const FILE_OWNERS_COLUMN_CONTENT = 'file-owners-column-content';
@customElement(FILE_OWNERS_COLUMN_CONTENT)
export class FileOwnersColumnContent extends common {
  @property({type: String})
  path?: string;

  @property({type: String})
  oldPath?: string;

  static override get styles() {
    return [];
  }

  override render() {
    console.log(
      `hidden: ${this.hidden}, userRole: ${this.userRole}, path: ${this.path}, oldPath: ${this.oldPath}`
    );
    return nothing;
  }
}

export function shouldHide(
  change?: ChangeInfo,
  patchRange?: PatchRange,
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
    change.submit_requirements &&
    change.submit_requirements.find(r => r.name === 'Owner-Approval')
  ) {
    return !userRole || userRole === UserRole.ANONYMOUS;
  }
  return true;
}
