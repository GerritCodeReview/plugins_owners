// Copyright (C) 2023 The Android Open Source Project
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

package com.googlesource.gerrit.owners;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LegacySubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRecordInfo;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

@TestPlugin(name = "owners", sysModule = "com.googlesource.gerrit.owners.OwnersModule")
@UseLocalDisk
public class OwnersSubmitRequirementIT extends OwnersSubmitRequirementITAbstract {
  private static final LegacySubmitRequirementInfo NOT_READY =
      new LegacySubmitRequirementInfo("NOT_READY", "Owners", "owners");
  private static final LegacySubmitRequirementInfo READY =
      new LegacySubmitRequirementInfo("OK", "Owners", "owners");

  @Inject private SitePaths sitePaths;

  @Override
  public void setUpTestPlugin() throws Exception {
    // ensure that `owners.enableSubmitRequirements = true` for each integration test without
    // relying on `GlobalPluginConfig` annotation
    FileBasedConfig ownersConfig =
        new FileBasedConfig(sitePaths.etc_dir.resolve("owners.config").toFile(), FS.DETECTED);
    ownersConfig.setBoolean("owners", null, "enableSubmitRequirement", true);
    ownersConfig.save();
    super.setUpTestPlugin();
  }

  @Override
  protected void verifyChangeNotReady(ChangeInfo notReady) {
    assertThat(notReady.requirements).containsExactly(NOT_READY);
  }

  @Override
  protected void verifyChangeReady(ChangeInfo ready) {
    assertThat(ready.requirements).containsExactly(READY);
  }

  @Override
  protected void verifyRuleError(ChangeInfo change) {
    assertThat(
            change.submitRecords.stream()
                .filter(record -> SubmitRecordInfo.Status.RULE_ERROR == record.status)
                .filter(record -> record.errorMessage.startsWith("Invalid owners file: OWNERS"))
                .findAny())
        .isPresent();
  }

  @Override
  protected void updateChildProjectConfiguration(NameKey childProject) {
    // there is no need to further customize project when `owners.enableSubmitRequirements = true`
    // as it is globally enabled
  }
}
