package com.vmware.gerrit.owners.common;

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

  private <T> Set<T> flattenSet(Optional<Stream<T>> optionalStream) {
    return optionalStream.orElse(Stream.empty()).collect(Collectors.toSet());
  }

  private void addMatchers(JsonNode jsonNode, OwnersConfig ret) {
    Optional<JsonNode> matchesNode =
        Optional.ofNullable(jsonNode.get("matches"));

    StreamUtils.optionalToStream(matchesNode).forEach(node -> {
      StreamUtils.asStream(node.iterator())
          .flatMap(matcher -> toMatcherStream(matcher))
          .forEach(ret::addMatcher);
    });
  }

  private static Stream<String> extractOwners(
      JsonNode node) {
    return StreamUtils.asStream(node.iterator())
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

  private Stream<Matcher> toMatcherStream(JsonNode element) {
    return StreamUtils.optionalToStream(toMatcher(element));
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
    Optional<Matcher> exactMatcher =
        getText(node, "exact").map(el -> new ExactMatcher(el, owners));

    return Optional.ofNullable(suffixMatcher
        .orElseGet(() -> regexMatcher
        .orElseGet(() -> exactMatcher
        .orElseGet(() -> {
          log.warn("Ignoring invalid element " + node.toString());
          return null;
        }))));
  }

  private Optional<String> getText(JsonNode node, String field) {
    return Optional.ofNullable(node.get(field).asText());
  }

  private Optional<JsonNode> getNode(JsonNode node, String field) {
    return Optional.ofNullable(node.get(field));
  }
}
