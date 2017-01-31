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

package com.vmware.gerrit.owners.common;

import static com.vmware.gerrit.owners.common.StreamUtils.iteratorStream;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gwtorm.server.OrmException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConfigurationParser {

  private static final Logger log =
      LoggerFactory.getLogger(OwnersConfig.class);
  private ReviewDb db;
  private AccountResolver resolver;

  public ConfigurationParser(AccountResolver resolver, ReviewDb db) {
    this.resolver = resolver;
    this.db = db;
  }

  public Optional<OwnersConfig> getOwnersConfig(byte[] yamlBytes) {
    try {
      final OwnersConfig ret = new OwnersConfig();
      JsonNode jsonNode = new ObjectMapper(new YAMLFactory())
          .readValue(yamlBytes, JsonNode.class);
      Boolean inherited = Optional.ofNullable(jsonNode.get("inherited"))
          .map(JsonNode::asBoolean).orElse(false);
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
    Optional<Stream<String>> owners =
        Optional.ofNullable(jsonNode.get("owners"))
        .map(ConfigurationParser::extractOwners);
    ret.setOwners(flattenSet(owners));

  }

  private static <T> Set<T> flattenSet(Optional<Stream<T>> optionalStream) {
    return optionalStream.orElse(Stream.empty()).collect(Collectors.toSet());
  }

  private void addMatchers(JsonNode jsonNode, OwnersConfig ret) {
    getNode(jsonNode, "matches")
    .map(this::getMatchers)
    .ifPresent(m -> m.forEach(ret::addMatcher));
  }

  private Stream<Matcher> getMatchers(JsonNode node) {
    return iteratorStream(node.iterator())
        .map(this::toMatcher)
        .filter(Optional::isPresent)
        .map(m -> m.get());
  }

  private static Stream<String> extractOwners(
      JsonNode node) {
    return iteratorStream(node.iterator())
            .map(JsonNode::asText);
  }

  /**
   * Translates emails to Account.Ids.
   *
   * @param emails emails to translate
   * @return set of account ids
   */
  Stream<Account.Id> getOwnersFromEmails(Stream<String> emails) {
    return emails.flatMap(mail -> resolveEmail(mail).stream());
  }

  private Set<Account.Id> resolveEmail(String email) {
    try {
      return resolver.findAll(db, email);
    } catch (OrmException e) {
      log.error("cannot resolve email " + email, e);
      return Collections.emptySet();
    }
  }

  private Optional<Matcher> toMatcher(JsonNode node) {
    Set<Id> owners =
        flattenSet(getNode(node, "owners").map(
            o -> getOwnersFromEmails(extractOwners(o))));
    if (owners.isEmpty()) {
      log.warn("Matches must contain a list of owners");
      return Optional.empty();
    }

    Optional<Matcher> suffixMatcher =
        getText(node, "suffix").map(el -> new SuffixMatcher(el, owners));
    Optional<Matcher> regexMatcher =
        getText(node, "regex").map(el -> new RegExMatcher(el, owners));
    Optional<Matcher> partialRegexMatcher =
        getText(node, "partial_regex").map(el -> new PartialRegExMatcher(el, owners));

    Optional<Matcher> exactMatcher =
        getText(node, "exact").map(el -> new ExactMatcher(el, owners));

    return Optional.ofNullable(suffixMatcher
        .orElseGet(() -> regexMatcher
        .orElseGet(() -> partialRegexMatcher
        .orElseGet(() -> exactMatcher
        .orElseGet(() -> {
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
