// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners.api;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.entities.Account.Id;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Scopes;
import java.util.Collection;
import org.junit.Test;

@TestPlugin(
    name = "owners-api",
    sysModule = "com.googlesource.gerrit.owners.api.OwnersAttentionSetIT$TestModule")
public class OwnersAttentionSetIT extends LightweightPluginDaemonTest {

  @Inject private DynamicItem<OwnersAttentionSet> ownerAttentionSetItem;

  @Override
  public Module createModule() {
    return new OwnersApiModule();
  }

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      DynamicItem.bind(binder(), OwnersAttentionSet.class)
          .to(SelectFirstOwnerForAttentionSet.class)
          .in(Scopes.SINGLETON);
    }
  }

  public static class SelectFirstOwnerForAttentionSet implements OwnersAttentionSet {
    @Override
    public Collection<Id> addToAttentionSet(ChangeInfo changeInfo, Collection<Id> owners) {
      return null;
    }
  }

  @Test
  public void shouldAllowOwnersAttentionSetOverride() {
    OwnersAttentionSet attentionSetSelector = ownerAttentionSetItem.get();

    assertThat(attentionSetSelector).isNotNull();
    assertThat(attentionSetSelector.getClass()).isEqualTo(SelectFirstOwnerForAttentionSet.class);
  }
}
