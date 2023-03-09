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
import java.util.Objects;
import java.util.Optional;

public class ScoreDefinition {
  public static final ScoreDefinition CODE_REVIEW = new ScoreDefinition(LabelId.CODE_REVIEW, null);
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String label;
  private final Optional<Integer> score;

  ScoreDefinition(String label, Integer score) {
    this.label = label;
    this.score = Optional.ofNullable(score);
  }

  public String getLabel() {
    return label;
  }

  public Optional<Integer> getScore() {
    return score;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("ScoreDefinition [label=");
    builder.append(label);
    builder.append(", score=");
    builder.append(score);
    builder.append("]");
    return builder.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, score);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if ((obj == null) || getClass() != obj.getClass()) {
      return false;
    }

    ScoreDefinition other = (ScoreDefinition) obj;
    return Objects.equals(label, other.label) && Objects.equals(score, other.score);
  }

  public static Optional<ScoreDefinition> parse(String definition) {
    return Optional.ofNullable(definition)
        .filter(def -> !def.isBlank())
        .map(
            def -> {
              int hasComma = def.indexOf(',');
              if (hasComma > 0) {
                try {
                  return new ScoreDefinition(
                      def.substring(0, hasComma).strip(),
                      Integer.valueOf(def.substring(hasComma + 1).trim()));

                } catch (NumberFormatException e) {
                  logger.atSevere().withCause(e).log(
                      "Parsing score definition [%s] has failed.", def);
                  return null;
                }
              }
              return new ScoreDefinition(def, null);
            });
  }
}
