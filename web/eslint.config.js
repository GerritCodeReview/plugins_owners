/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

const {defineConfig} = require('eslint/config');

// eslint-disable-next-line no-undef
__plugindir = 'owners/web';

const gerritEslint = require('../../eslint.config.js');

module.exports = defineConfig([
  {
    extends: [gerritEslint],
  },
]);
