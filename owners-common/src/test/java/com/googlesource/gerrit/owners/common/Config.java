// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners.common;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;

import com.google.common.base.Charsets;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Repository;
import org.junit.Ignore;
import org.powermock.api.easymock.PowerMock;

@Ignore
public abstract class Config {
  protected GitRepositoryManager repositoryManager;
  protected Repository repository;
  protected PatchList patchList;
  protected ConfigurationParser parser;
  protected TestAccounts accounts = new TestAccounts();
  protected Optional<String> branch = Optional.of("master");

  public void setup() throws Exception {
    PowerMock.mockStatic(JgitWrapper.class);

    repositoryManager = PowerMock.createMock(GitRepositoryManager.class);
    repository = PowerMock.createMock(Repository.class);
    parser = new ConfigurationParser(accounts);
  }

  void expectConfig(String path, String config) throws IOException {
    expect(
            JgitWrapper.getBlobAsBytes(
                anyObject(Repository.class), anyObject(String.class), eq(path)))
        .andReturn(Optional.of(config.getBytes()))
        .anyTimes();
  }

  void expectConfig(String path, String revision, String config) throws IOException {
    expect(JgitWrapper.getBlobAsBytes(anyObject(Repository.class), eq(revision), eq(path)))
        .andReturn(Optional.of(config.getBytes()))
        .anyTimes();
  }

  void expectNoConfig(String path) throws IOException {
    expect(
            JgitWrapper.getBlobAsBytes(
                anyObject(Repository.class), anyObject(String.class), eq(path)))
        .andReturn(Optional.empty())
        .anyTimes();
  }

  void creatingPatch(String... fileNames) {
    creatingPatchList(Arrays.asList(fileNames));
  }

  void creatingPatchList(List<String> names) {
    patchList = PowerMock.createMock(PatchList.class);
    List<PatchListEntry> entries =
        names.stream().map(name -> expectEntry(name)).collect(Collectors.toList());
    expect(patchList.getPatches()).andReturn(entries);
  }

  PatchListEntry expectEntry(String name) {
    PatchListEntry entry = PowerMock.createMock(PatchListEntry.class);
    expect(entry.getNewName()).andReturn(name).anyTimes();
    expect(entry.getChangeType()).andReturn(Patch.ChangeType.MODIFIED).anyTimes();
    expect(entry.getDeletions()).andReturn(1);
    expect(entry.getInsertions()).andReturn(1);
    return entry;
  }

  Optional<OwnersConfig> getOwnersConfig(String string) {
    return parser.getOwnersConfig(string.getBytes(Charsets.UTF_8));
  }

  public String createConfig(boolean inherited, String[] owners, MatcherConfig... matchers) {
    StringBuilder sb = new StringBuilder();
    sb.append("inherited: " + inherited + "\n");
    if (owners.length > 0) {
      sb.append("owners: \n");
      for (String owner : owners) {
        sb.append("- " + owner + "\n");
      }
    }
    if (matchers.length > 0) {
      sb.append("matchers: \n");
      for (MatcherConfig matcher : matchers) {
        sb.append(matcher.toYaml());
      }
    }
    return sb.toString();
  }

  public String[] owners(String... owners) {
    return owners;
  }
}
