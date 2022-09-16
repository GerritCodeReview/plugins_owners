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
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.owners.common;

import static com.googlesource.gerrit.owners.common.StreamUtils.iteratorStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gerrit.entities.Account.Id;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationParser {
  private static final Logger log = LoggerFactory.getLogger(OwnersConfig.class);
  private Accounts accounts;

  public ConfigurationParser(Accounts accounts) {
    this.accounts = accounts;
  }

  public Optional<OwnersConfig> getOwnersConfig(byte[] yamlBytes) {
    try {
      final OwnersConfig ret = new OwnersConfig();
      JsonNode jsonNode = new ObjectMapper(new YAMLFactory()).readValue(yamlBytes, JsonNode.class);
      Boolean inherited =
          Optional.ofNullable(jsonNode.get("inherited")).map(JsonNode::asBoolean).orElse(true);
      ret.setInherited(inherited);
      addClassicMatcher(jsonNode, ret);
      addMatchers(jsonNode, ret);
      return Optional.of(ret);
    } catch (IOException e) {
      log.warn("Unable to read YAML Owners file", e);
      return Optional.empty();
    }
  }

  private void addClassicMatcher(JsonNode jsonNode, OwnersConfig ret) {
    ret.setOwners(toClassicOwnersList(jsonNode, "owners").collect(Collectors.toSet()));
    ret.setReviewers(toClassicOwnersList(jsonNode, "reviewers").collect(Collectors.toSet()));
  }

  private void addMatchers(JsonNode jsonNode, OwnersConfig ret) {
    getNode(jsonNode, "matchers")
        .map(m -> getMatchers(m))
        .ifPresent(m -> m.forEach(ret::addMatcher));
  }

  private Stream<Matcher> getMatchers(JsonNode node) {
    return iteratorStream(node.iterator())
        .map(this::toMatcher)
        .filter(Optional::isPresent)
        .map(m -> m.get());
  }

  private static Stream<String> extractAsText(JsonNode node) {
    if (node.isTextual()) {
      return Stream.of(node.asText());
    }
    return iteratorStream(node.iterator()).map(JsonNode::asText);
  }

  private static <T> Stream<T> flatten(Optional<Stream<T>> optionalStream) {
    return optionalStream.orElse(Stream.empty());
  }

  private Stream<String> toClassicOwnersList(JsonNode jsonNode, String sectionName) {
    Stream<String> ownersStream =
        Optional.ofNullable(jsonNode.get(sectionName))
            .map(ConfigurationParser::extractAsText)
            .orElse(Stream.empty());
    return ownersStream;
  }

  private Optional<Matcher> toMatcher(JsonNode node) {
    Set<Id> owners =
        getNode(node, "owners")
            .map(ConfigurationParser::extractAsText)
            .orElse(Stream.empty())
            .flatMap(o -> accounts.find(o).stream())
            .collect(Collectors.toSet());
    Set<String> groupOwners =
        flatten(getNode(node, "owners").map(ConfigurationParser::extractAsText))
            .collect(Collectors.toSet());
    Set<Id> reviewers =
        getNode(node, "reviewers")
            .map(ConfigurationParser::extractAsText)
            .orElse(Stream.empty())
            .flatMap(o -> accounts.find(o).stream())
            .collect(Collectors.toSet());

    Optional<Matcher> suffixMatcher =
        getText(node, "suffix").map(el -> new SuffixMatcher(el, owners, reviewers, groupOwners));
    Optional<Matcher> regexMatcher =
        getText(node, "regex").map(el -> new RegExMatcher(el, owners, reviewers, groupOwners));
    Optional<Matcher> partialRegexMatcher =
        getText(node, "partial_regex")
            .map(el -> new PartialRegExMatcher(el, owners, reviewers, groupOwners));
    Optional<Matcher> exactMatcher =
        getText(node, "exact").map(el -> new ExactMatcher(el, owners, reviewers, groupOwners));

    return Optional.ofNullable(
        suffixMatcher.orElseGet(
            () ->
                regexMatcher.orElseGet(
                    () ->
                        partialRegexMatcher.orElseGet(
                            () ->
                                exactMatcher.orElseGet(
                                    () -> {
                                      log.warn("Ignoring invalid element " + node.toString());
                                      return null;
                                    })))));
  }

  private static Optional<String> getText(JsonNode node, String field) {
    return Optional.ofNullable(node.get(field)).map(JsonNode::asText);
  }

  private static Optional<JsonNode> getNode(JsonNode node, String field) {
    return Optional.ofNullable(node.get(field));
  }
}
