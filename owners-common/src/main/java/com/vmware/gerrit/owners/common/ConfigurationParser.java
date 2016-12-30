package com.vmware.gerrit.owners.common;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gwtorm.server.OrmException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConfigurationParser {

  private static final Logger log = LoggerFactory.getLogger(OwnersConfig.class);
  private ReviewDb db;
  private AccountResolver resolver;

  public ConfigurationParser(AccountResolver resolver, ReviewDb db) {
    this.resolver = resolver;
    this.db = db;
  }


  /**
   * Parse configuration
   */
  public Optional<OwnersConfig> parseYaml(String partial, String yamlString) {
    return parseYaml(partial, yamlString.getBytes(Charsets.UTF_8));
  }

  public Optional<OwnersConfig> parseYaml(String partial, byte[] yamlBytes) {
    try {
      final OwnersConfig ret = new OwnersConfig();
      JsonNode jsonNode = new ObjectMapper(new YAMLFactory()).readValue(yamlBytes, JsonNode.class);
      Boolean inherited = Optional.ofNullable(jsonNode.get("inherited")).map(JsonNode::asBoolean).orElse(false);
      ret.setInherited(inherited);
      addClassicMatcher(partial, jsonNode, ret);
      addMatchers(partial, jsonNode, ret);
      return Optional.of(ret);
    } catch (IOException e) {
      log.warn("Unable to parse YAML Owners file", e);
      return Optional.empty();
    }
  }

  private void addClassicMatcher(String partial, JsonNode jsonNode, OwnersConfig ret) {
    JsonNode ownersNode = jsonNode.get("owners");
    if(ownersNode != null) {
        ret.setOwners(extractOwners(ownersNode));
    }
  }

  private void addMatchers(String partial, JsonNode jsonNode,
      OwnersConfig ret) {
    JsonNode matchesNode = jsonNode.get("matches");
    if(matchesNode != null) {
      StreamUtils
          .asStream(matchesNode.iterator())
          .map(node -> toMatcher(node))
          .filter(Objects::nonNull)
          .forEach(t -> ret.addMatcher(partial + t.getPath(), t));
    }


  }

  private static Set<String> extractOwners(JsonNode ownersNode) {
    return StreamUtils
        .asStream(ownersNode.iterator())
        .map(node -> node.asText())
        .collect(Collectors.toSet());
  }

  /**
   * Translates emails to Account.Ids.
   * @param emails emails to translate
   * @return set of account ids
   */
  Set<Account.Id> getOwnersFromEmails(Set<String> emails) throws OrmException {
    Set<Account.Id> result = Sets.newHashSet();
    for (String email : emails) {
      Set<Account.Id> ids = resolver.findAll(db, email);
      result.addAll(ids);
    }
    return result;
  }

  private Matcher toMatcher(JsonNode element) {

    JsonNode ownersNode = element.get("owners");
    if(ownersNode == null) {
      log.warn("Matches must contain a list of owners");
      return null;
    }
    Set<String> ownersEmails = extractOwners(ownersNode);

    Set<Account.Id> ownersIds = Sets.newHashSet();
    try {
      ownersIds = getOwnersFromEmails(ownersEmails);
    } catch (OrmException e) {
      log.warn("Returned Orm Exception "+e.getMessage());
    }


    JsonNode suffixValue = element.get("suffix");
    if(suffixValue != null) {
      return new SuffixMatcher(suffixValue.asText(), ownersIds); // suffixValue,element.get("owners"));
    }
    JsonNode regexValue = element.get("regex");
    if(regexValue != null) {
      return new RegExMatcher(regexValue.asText(),ownersIds);
    }
    JsonNode exactValue = element.get("exact");
    if(exactValue != null) {
      return new ExactMatcher(exactValue.asText(), ownersIds);
    }
    log.warn("Ignoring invalid element "+element.toString());
    return null;

  }
}
