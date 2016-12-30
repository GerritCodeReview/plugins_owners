package com.vmware.gerrit.owners.common;

import com.google.common.collect.Sets;
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
    Optional<JsonNode> ownersNode =
        Optional.ofNullable(jsonNode.get("owners"));
    ret.setOwners(extractOwners(ownersNode));

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

  private static Set<String> extractOwners(
      Optional<JsonNode> ownersNode) {

    return ownersNode
        .map(node -> StreamUtils.asStream(node.iterator())
            .map(JsonNode::asText).collect(Collectors.toSet()))
        .orElse(Sets.newHashSet());
  }

  /**
   * Translates emails to Account.Ids.
   *
   * @param emails emails to translate
   * @return set of account ids
   */
  Set<Account.Id> getOwnersFromEmails(Set<String> emails) {

    Set<Account.Id> result = Sets.newHashSet();
    emails.stream().forEach(email -> {
      try {
        Set<Id> foundIds = resolver.findAll(db, email);
        result.addAll(foundIds);
      } catch (OrmException e) {
        log.error("cannot resolve emails", e);
      }
    });

    return result;
  }

  private Stream<Matcher> toMatcherStream(JsonNode element) {
    return StreamUtils.optionalToStream(toMatcher(element));
  }

  private Optional<Matcher> toMatcher(JsonNode element) {

    Optional<JsonNode> ownersNode =
        Optional.ofNullable(element.get("owners"));
    if (!ownersNode.isPresent()) {
      log.warn("Matches must contain a list of owners");
      return Optional.empty();
    }
    Set<String> ownersEmails = extractOwners(ownersNode);

    Set<Account.Id> ownersIds = Sets.newHashSet();
    ownersIds = getOwnersFromEmails(ownersEmails);


    Optional<JsonNode> suffixValue =
        Optional.ofNullable(element.get("suffix"));
    if (suffixValue.isPresent()) {
      return Optional
          .of(new SuffixMatcher(suffixValue.get().asText(), ownersIds));
    }
    Optional<JsonNode> regexValue =
        Optional.ofNullable(element.get("regex"));
    if (regexValue.isPresent()) {
      return Optional
          .of(new RegExMatcher(regexValue.get().asText(), ownersIds));
    }
    Optional<JsonNode> exactValue =
        Optional.ofNullable(element.get("exact"));
    if (exactValue.isPresent()) {
      return Optional
          .of(new ExactMatcher(exactValue.get().asText(), ownersIds));
    }
    log.warn("Ignoring invalid element " + element.toString());
    return Optional.empty();

  }
}
