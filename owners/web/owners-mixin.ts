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
import {LitElement, PropertyValues} from 'lit';
import {property, state} from 'lit/decorators';
import {ChangeInfo} from '@gerritcodereview/typescript-api/rest-api';
import {FilesOwners, OwnersService} from './owners-service';
import {RestPluginApi} from '@gerritcodereview/typescript-api/rest';
import {ModelLoader, OwnersModel, PatchRange, UserRole} from './owners-model';

// Lit mixin definition as described in https://lit.dev/docs/composition/mixins/
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Constructor<T> = new (...args: any[]) => T;

export interface OwnersInterface extends LitElement {
  change?: ChangeInfo;
  patchRange?: PatchRange;
  restApi?: RestPluginApi;
  userRole?: UserRole;
  allFilesApproved?: boolean;
  filesOwners?: FilesOwners;

  onModelUpdate(): void;
}

export const OwnersMixin = <T extends Constructor<LitElement>>(
  superClass: T
) => {
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

  return Mixin as unknown as T & Constructor<OwnersInterface>;
};
