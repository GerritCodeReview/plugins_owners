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

package com.googlesource.gerrit.owners.common;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Describes the label together with score (the latter is optional) that is configured in the OWNERS
 * file. File owners have to give the score for change to be submittable.
 */
public class LabelDefinition {
  public static final LabelDefinition CODE_REVIEW = new LabelDefinition(LabelType.create(LabelId.CODE_REVIEW, defaultCodeReviewLabelMinMax()), (short)2);

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Pattern LABEL_PATTERN =
      Pattern.compile("^([a-zA-Z0-9-]+)(?:(?:\\s*,\\s*)(\\d))?$");

  public static final String MISSING_CODE_REVIEW_LABEL =
      "Cannot calculate file owners state when review label is not configured";

  private final LabelType labelType;
  private final Short score;

  public LabelDefinition(LabelType name, Short score) {
    this.labelType = name;
    this.score = score;
  }

  public LabelType getLabelType() {
    return labelType;
  }

  public Short getScore() {
    return score;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("LabelDefinition [name=");
    builder.append(labelType);
    builder.append(", score=");
    builder.append(score);
    builder.append("]");
    return builder.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(labelType, score);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if ((obj == null) || getClass() != obj.getClass()) {
      return false;
    }

    LabelDefinition other = (LabelDefinition) obj;
    return Objects.equals(labelType, other.labelType) && Objects.equals(score, other.score);
  }

  public static Optional<LabelDefinition> parse(String definition) {
    return Optional.ofNullable(definition)
        .filter(def -> !def.isBlank())
        .map(
            def -> {
              Matcher labelDef = LABEL_PATTERN.matcher(def.trim());
              if (!labelDef.matches()) {
                logger.atSevere().log("Parsing label definition [%s] has failed.", def);
                return null;
              }

              return new LabelDefinition(
                  LabelType.withDefaultValues(labelDef.group(1)),
                  Optional.ofNullable(labelDef.group(2)).map(Short::valueOf).orElse(null));
            });
  }

  private static List<LabelValue> defaultCodeReviewLabelMinMax() {
    List<LabelValue> values = new ArrayList<>(2);
    values.add(LabelValue.create((short) -2, "Rejected"));
    values.add(LabelValue.create((short) -1, "Needs Improvement"));
    values.add(LabelValue.create((short) 0, "Neutral"));
    values.add(LabelValue.create((short) 1, "Needs Approval"));
    values.add(LabelValue.create((short) 2, "Approved"));

    return values;
  }
  public static LabelDefinition resolveLabel(LabelTypes labelTypes, Optional<LabelDefinition> maybeOwnersFileApproveLabel, Project.NameKey project)
      throws ResourceNotFoundException {
    LabelDefinition labelDefinition = maybeOwnersFileApproveLabel.flatMap(ownersFileApproveLabel -> labelTypes
        .byLabel(ownersFileApproveLabel.getLabelType().getName())
        .map(type -> new LabelDefinition(type, ownersFileApproveLabel.getScore())))
        .orElse(LabelDefinition.CODE_REVIEW);

    return labelIfExists(labelTypes, labelDefinition, project);
  }

  private static LabelDefinition labelIfExists(LabelTypes labelTypes, LabelDefinition labelDefinition, Project.NameKey project) throws ResourceNotFoundException {
    if (labelTypes
        .byLabel(labelDefinition.getLabelType().getLabelId()).isPresent()) {
      return labelDefinition;
    } else {
      LabelNotFoundException labelNotFoundException = new LabelNotFoundException(project, labelDefinition.getLabelType().getLabelId().get());
      logger.atInfo().withCause(labelNotFoundException).log("Invalid configuration");
      throw new ResourceNotFoundException(MISSING_CODE_REVIEW_LABEL, labelNotFoundException);
    }
  }

  public static class LabelNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    LabelNotFoundException(Project.NameKey project, String labelId) {
      super(String.format("Project %s has no %s label defined", project, labelId));
    }
  }
}
