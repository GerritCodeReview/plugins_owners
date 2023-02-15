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
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.server.project.testing.TestLabels.codeReview;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static com.google.gerrit.server.project.testing.TestLabels.verified;
import static com.googlesource.gerrit.owners.OwnersSubmitRequirement.hasSufficientApproval;
import static com.googlesource.gerrit.owners.OwnersSubmitRequirement.isApprovalMissing;
import static com.googlesource.gerrit.owners.OwnersSubmitRequirement.isApprovedByOwner;
import static com.googlesource.gerrit.owners.OwnersSubmitRequirement.isLabelApproved;
import static com.googlesource.gerrit.owners.OwnersSubmitRequirement.resolveLabel;
import static org.mockito.Mockito.mock;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;

public class OwnersSubmitRequirementTest {
  private static String LABEL_ID = "foo";
  private static int MAX_LABEL_VALUE = 1;
  private static Project.NameKey PROJECT = Project.nameKey("project");

  @Test
  public void shouldResolveLabelToConfiguredOne() {
    // when
    String label = resolveLabel(null, Optional.of(LABEL_ID));

    // then
    assertThat(label).isEqualTo(LABEL_ID);
  }

  @Test
  public void shouldResolveLabelToCodeReviewIfProjectHasCodeReviewLabelConfigured() {
    // given
    LabelTypes types =
        new LabelTypes(
            List.of(verified(), label().setFunction(LabelFunction.NO_BLOCK).build(), codeReview()));

    // when
    String label = resolveLabel(types, Optional.empty());

    // then
    assertThat(label).isEqualTo(LabelId.CODE_REVIEW);
  }

  @Test
  public void shouldOwnersLabelContainOnlyConfiguredLabel() {
    // when
    LabelTypes result =
        OwnersSubmitRequirement.ownersLabel(
            new LabelTypes(List.of(label().build())), LABEL_ID, PROJECT);

    // then
    assertThat(result.getLabelTypes()).hasSize(1);
    assertThat(result.byLabel(LABEL_ID)).isPresent();
  }

  @Test
  public void shouldOwnersLabelBeEmptyIfNonExistingLabelIsConfigured() {
    // when
    LabelTypes result =
        OwnersSubmitRequirement.ownersLabel(
            new LabelTypes(List.of(codeReview())), LABEL_ID, PROJECT);

    // then
    assertThat(result.getLabelTypes()).isEmpty();
  }

  @Test
  public void shouldApprovalBeMissingWhenSomeoneElseApproved() {
    // given
    Account.Id fileOwner = mock(Account.Id.class);
    Account.Id uploader = mock(Account.Id.class);
    LabelTypes labelTypes = maxNoBlockLabelFooTypes();
    Map<Account.Id, List<PatchSetApproval>> uploaderApproval =
        Map.of(uploader, List.of(approvedBy(uploader, LABEL_ID, MAX_LABEL_VALUE)));

    // when
    boolean isApprovalMissing =
        isApprovalMissing(
            Map.entry("path", Set.of(fileOwner)), uploader, uploaderApproval, labelTypes);

    // then
    assertThat(isApprovalMissing).isTrue();
  }

  @Test
  public void shouldApprovalBeNotMissingWhenApprovedByFileOwner() {
    // given
    Account.Id fileOwner = mock(Account.Id.class);
    Account.Id uploader = mock(Account.Id.class);
    LabelTypes labelTypes = maxNoBlockLabelFooTypes();
    Map<Account.Id, List<PatchSetApproval>> fileOwnerApproval =
        Map.of(fileOwner, List.of(approvedBy(fileOwner, LABEL_ID, MAX_LABEL_VALUE)));

    // when
    boolean isApprovalMissing =
        isApprovalMissing(
            Map.entry("path", Set.of(fileOwner)), uploader, fileOwnerApproval, labelTypes);

    // then
    assertThat(isApprovalMissing).isFalse();
  }

  @Test
  public void shouldApprovalBeNotMissingWhenApprovedByAtLeastOneOwner() {
    // given
    Account.Id fileOwnerA = mock(Account.Id.class);
    Account.Id fileOwnerB = mock(Account.Id.class);
    Account.Id uploader = mock(Account.Id.class);
    LabelTypes labelTypes = maxNoBlockLabelFooTypes();
    Map<Account.Id, List<PatchSetApproval>> fileOwnerApproval =
        Map.of(fileOwnerA, List.of(approvedBy(fileOwnerA, LABEL_ID, MAX_LABEL_VALUE)));

    // when
    boolean isApprovalMissing =
        isApprovalMissing(
            Map.entry("path", Set.of(fileOwnerA, fileOwnerB)),
            uploader,
            fileOwnerApproval,
            labelTypes);

    // then
    assertThat(isApprovalMissing).isFalse();
  }

  @Test
  public void shouldNotBeApprovedByOwnerWhenSomeoneElseApproved() {
    // given
    Account.Id fileOwner = mock(Account.Id.class);
    Account.Id uploader = mock(Account.Id.class);
    LabelTypes labelTypes = maxNoBlockLabelFooTypes();
    Map<Account.Id, List<PatchSetApproval>> uploaderApproval =
        Map.of(uploader, List.of(approvedBy(uploader, LABEL_ID, MAX_LABEL_VALUE)));

    // when
    boolean approvedByOwner = isApprovedByOwner(fileOwner, fileOwner, uploaderApproval, labelTypes);

    // then
    assertThat(approvedByOwner).isFalse();
  }

  @Test
  public void shouldNotBeApprovedWhenApprovalGivenForDifferentLabel() {
    // given
    Account.Id fileOwner = mock(Account.Id.class);
    LabelTypes labelTypes =
        new LabelTypes(
            List.of(label().setName("bar").setFunction(LabelFunction.MAX_NO_BLOCK).build()));
    Map<Account.Id, List<PatchSetApproval>> fileOwnerForDifferentLabelApproval =
        Map.of(fileOwner, List.of(approvedBy(fileOwner, LABEL_ID, MAX_LABEL_VALUE)));

    // when
    boolean approvedByOwner =
        isApprovedByOwner(fileOwner, fileOwner, fileOwnerForDifferentLabelApproval, labelTypes);

    // then
    assertThat(approvedByOwner).isFalse();
  }

  @Test
  public void shouldBeApprovedByOwner() {
    // given
    Account.Id fileOwner = mock(Account.Id.class);
    LabelTypes labelTypes = maxNoBlockLabelFooTypes();
    Map<Account.Id, List<PatchSetApproval>> fileOwnerApproval =
        Map.of(fileOwner, List.of(approvedBy(fileOwner, LABEL_ID, MAX_LABEL_VALUE)));

    // when
    boolean approvedByOwner =
        isApprovedByOwner(fileOwner, fileOwner, fileOwnerApproval, labelTypes);

    // then
    assertThat(approvedByOwner).isTrue();
  }

  @Test
  public void shouldHaveNotSufficientApprovalWhenLabelIsNotApproved() {
    // given
    LabelType maxValueRequired = label().setFunction(LabelFunction.MAX_NO_BLOCK).build();
    Account.Id fileOwner = mock(Account.Id.class);
    LabelTypes labelTypes = new LabelTypes(List.of(maxValueRequired));

    // when
    boolean hasSufficientApproval =
        hasSufficientApproval(approvedBy(fileOwner, LABEL_ID, 0), labelTypes, fileOwner, fileOwner);

    // then
    assertThat(hasSufficientApproval).isFalse();
  }

  @Test
  public void shouldHaveNotSufficientApprovalWhenLabelDoesntMatch() {
    // given
    Account.Id fileOwner = mock(Account.Id.class);
    LabelTypes labelTypes = new LabelTypes(Collections.emptyList());

    // when
    boolean hasSufficientApproval =
        hasSufficientApproval(approvedBy(fileOwner, LABEL_ID, 0), labelTypes, fileOwner, fileOwner);

    // then
    assertThat(hasSufficientApproval).isFalse();
  }

  @Test
  public void shouldHaveSufficientApprovalWhenLabelIsApproved() {
    // given
    LabelType maxValueRequired = label().setFunction(LabelFunction.MAX_NO_BLOCK).build();
    Account.Id fileOwner = mock(Account.Id.class);
    LabelTypes labelTypes = new LabelTypes(List.of(maxValueRequired));

    // when
    boolean hasSufficientApproval =
        hasSufficientApproval(
            approvedBy(fileOwner, LABEL_ID, MAX_LABEL_VALUE), labelTypes, fileOwner, fileOwner);

    // then
    assertThat(hasSufficientApproval).isTrue();
  }

  @Test
  public void labelShouldNotBeApprovedWhenSelfApprovalIsDisabledAndOwnerApproved() {
    // given
    LabelType ignoreSelfApproval = label().setIgnoreSelfApproval(true).build();
    Account.Id fileOwner = mock(Account.Id.class);

    // when
    boolean approved =
        isLabelApproved(
            ignoreSelfApproval,
            fileOwner,
            fileOwner,
            approvedBy(fileOwner, LABEL_ID, MAX_LABEL_VALUE));

    // then
    assertThat(approved).isFalse();
  }

  @Test
  public void labelShouldNotBeApprovedWhenMaxValueIsRequiredButNotProvided() {
    // given
    LabelType maxValueRequired = label().setFunction(LabelFunction.MAX_NO_BLOCK).build();
    Account.Id fileOwner = mock(Account.Id.class);

    // when
    boolean approved =
        isLabelApproved(maxValueRequired, fileOwner, fileOwner, approvedBy(fileOwner, LABEL_ID, 0));

    // then
    assertThat(approved).isFalse();
  }

  @Test
  public void labelShouldBeApprovedWhenMaxValueIsRequiredAndProvided() {
    // given
    LabelType maxValueRequired = label().setFunction(LabelFunction.MAX_NO_BLOCK).build();
    Account.Id fileOwner = mock(Account.Id.class);

    // when
    boolean approved =
        isLabelApproved(
            maxValueRequired,
            fileOwner,
            fileOwner,
            approvedBy(fileOwner, LABEL_ID, MAX_LABEL_VALUE));

    // then
    assertThat(approved).isTrue();
  }

  @Test
  public void labelShouldNotBeApprovedWhenAnyValueWithBlockIsConfiguredAndMaxNegativeIsProvided() {
    // given
    LabelType anyWithBlock = label().setFunction(LabelFunction.ANY_WITH_BLOCK).build();
    Account.Id fileOwner = mock(Account.Id.class);

    // when
    boolean approved =
        isLabelApproved(anyWithBlock, fileOwner, fileOwner, approvedBy(fileOwner, LABEL_ID, -1));

    // then
    assertThat(approved).isFalse();
  }

  @Test
  public void labelShouldBeApprovedWhenAnyValueWithBlockIsConfiguredAndPositiveValueIsProvided() {
    // given
    LabelType anyWithBlock =
        label()
            .setValues(
                Arrays.asList(
                    value(2, "Approved"),
                    value(1, "OK"),
                    value(0, "No score"),
                    value(-1, "Blocked")))
            .setFunction(LabelFunction.ANY_WITH_BLOCK)
            .build();
    Account.Id fileOwner = mock(Account.Id.class);

    // when
    boolean approved =
        isLabelApproved(anyWithBlock, fileOwner, fileOwner, approvedBy(fileOwner, LABEL_ID, 1));

    // then
    assertThat(approved).isTrue();
  }

  @Test
  public void labelShouldNotBeApprovedWhenAnyValueWithBlockIsConfiguredAndDefaultValueIsProvided() {
    // given
    LabelType anyWithBlock =
        label()
            .setValues(
                Arrays.asList(
                    value(2, "Approved"),
                    value(1, "OK"),
                    value(0, "No score"),
                    value(-1, "Blocked")))
            .setFunction(LabelFunction.ANY_WITH_BLOCK)
            .build();
    Account.Id fileOwner = mock(Account.Id.class);

    // when
    boolean approved =
        isLabelApproved(anyWithBlock, fileOwner, fileOwner, approvedBy(fileOwner, LABEL_ID, 0));

    // then
    assertThat(approved).isFalse();
  }

  private static final LabelTypes maxNoBlockLabelFooTypes() {
    LabelType maxValueRequired = label().setFunction(LabelFunction.MAX_NO_BLOCK).build();
    return new LabelTypes(List.of(maxValueRequired));
  }

  private static final LabelType.Builder label() {
    return labelBuilder(
        LABEL_ID, value(MAX_LABEL_VALUE, "Approved"), value(0, "No score"), value(-1, "Blocked"));
  }

  private static final PatchSetApproval approvedBy(Account.Id approving, String label, int value) {
    return PatchSetApproval.builder()
        .key(PatchSetApproval.key(mock(PatchSet.Id.class), approving, LabelId.create(label)))
        .granted(Timestamp.from(Instant.now()))
        .realAccountId(approving)
        .value(value)
        .build();
  }
}
