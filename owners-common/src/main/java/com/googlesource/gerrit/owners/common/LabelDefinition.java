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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Describes the label together with score (the latter is optional) that is configured in the OWNERS
 * file. File owners have to give the score for change to be submittable.
 */
public class LabelDefinition {
  public static final LabelDefinition CODE_REVIEW = new LabelDefinition(LabelId.CODE_REVIEW, null);

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Pattern LABEL_PATTERN =
      Pattern.compile("^([a-zA-Z0-9-]+)(?:(?:\\s*,\\s*)(\\d))?$");

  private final String name;
  private final Optional<Short> score;

  LabelDefinition(String name, Short score) {
    this.name = name;
    this.score = Optional.ofNullable(score);
  }

  public String getName() {
    return name;
  }

  public Optional<Short> getScore() {
    return score;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("LabelDefinition [name=");
    builder.append(name);
    builder.append(", score=");
    builder.append(score);
    builder.append("]");
    return builder.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, score);
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
    return Objects.equals(name, other.name) && Objects.equals(score, other.score);
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
                  labelDef.group(1),
                  Optional.ofNullable(labelDef.group(2)).map(Short::valueOf).orElse(null));
            });
  }
}
