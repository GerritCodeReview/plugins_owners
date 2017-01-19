package com.vmware.gerrit.owners.common;

import com.google.common.base.Charsets;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountResolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class ConfigurationParser {

  private static final Logger log =
      LoggerFactory.getLogger(ConfigurationParser.class);

  Optional<OwnersConfig> getOwnersConfig(byte[] yamlBytes) {
    try {
      return Optional.of(new ObjectMapper(new YAMLFactory())
          .readValue(yamlBytes, OwnersConfig.class));
    } catch (IOException e) {
      log.warn("Unable to parse YAML Owners file", e);
      return Optional.empty();
    }
  }

}
