/**
 * @license
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {SpecialFilePath} from './gerrit-model';

export function deepEqual<T>(a: T, b: T): boolean {
  if (a === b) return true;
  if (a === undefined || b === undefined) return false;
  if (a === null || b === null) return false;
  if (a instanceof Date || b instanceof Date) {
    if (!(a instanceof Date && b instanceof Date)) return false;
    return a.getTime() === b.getTime();
  }

  if (a instanceof Set || b instanceof Set) {
    if (!(a instanceof Set && b instanceof Set)) return false;
    if (a.size !== b.size) return false;
    for (const ai of a) if (!b.has(ai)) return false;
    return true;
  }
  if (a instanceof Map || b instanceof Map) {
    if (!(a instanceof Map && b instanceof Map)) return false;
    if (a.size !== b.size) return false;
    for (const [aKey, aValue] of a.entries()) {
      if (!b.has(aKey) || !deepEqual(aValue, b.get(aKey))) return false;
    }
    return true;
  }

  if (typeof a === 'object') {
    if (typeof b !== 'object') return false;
    const aObj = a as Record<string, unknown>;
    const bObj = b as Record<string, unknown>;
    const aKeys = Object.keys(aObj);
    const bKeys = Object.keys(bObj);
    if (aKeys.length !== bKeys.length) return false;
    for (const key of aKeys) {
      if (!deepEqual(aObj[key], bObj[key])) return false;
    }
    return true;
  }

  return false;
}

/**
 * Queries for child element specified with a selector. Copied from Gerrit's common-util.ts.
 */
export function query<E extends Element = Element>(
  el: Element | null | undefined,
  selector: string
): E | undefined {
  if (!el) return undefined;
  if (el.shadowRoot) {
    const r = el.shadowRoot.querySelector<E>(selector);
    if (r) return r;
  }
  return el.querySelector<E>(selector) ?? undefined;
}

/**
 * Copied from Gerrit's path-list-util.ts.
 */
export function computeDisplayPath(path?: string) {
  if (path === SpecialFilePath.COMMIT_MESSAGE) {
    return 'Commit message';
  } else if (path === SpecialFilePath.MERGE_LIST) {
    return 'Merge list';
  }
  return path ?? '';
}

/**
 *  Separates a path into:
 *  - The part that matches another path,
 *  - The part that does not match the other path,
 *  - The file name
 *
 *  For example:
 *    diffFilePaths('same/part/new/part/foo.js', 'same/part/different/foo.js');
 *  yields: {
 *      matchingFolders: 'same/part/',
 *      newFolders: 'new/part/',
 *      fileName: 'foo.js',
 *    }
 *
 *  Copied from Gerrit's string-util.ts.
 */
export function diffFilePaths(filePath: string, otherFilePath?: string) {
  // Separate each string into an array of folder names + file name.
  const displayPath = computeDisplayPath(filePath);
  const previousFileDisplayPath = computeDisplayPath(otherFilePath);
  const displayPathParts = displayPath.split('/');
  const previousFileDisplayPathParts = previousFileDisplayPath.split('/');

  // Construct separate strings for matching folders, new folders, and file
  // name.
  const firstDifferencePartIndex = displayPathParts.findIndex(
    (part, index) => previousFileDisplayPathParts[index] !== part
  );
  const matchingSection = displayPathParts
    .slice(0, firstDifferencePartIndex)
    .join('/');
  const newFolderSection = displayPathParts
    .slice(firstDifferencePartIndex, -1)
    .join('/');
  const fileNameSection = displayPathParts[displayPathParts.length - 1];

  // Note: folder sections need '/' appended back.
  return {
    matchingFolders: matchingSection.length > 0 ? `${matchingSection}/` : '',
    newFolders: newFolderSection.length > 0 ? `${newFolderSection}/` : '',
    fileName: fileNameSection,
  };
}

/**
 * Truncates URLs to display filename only
 * Example
 * // returns '.../text.html'
 * util.truncatePath.('dir/text.html');
 * Example
 * // returns 'text.html'
 * util.truncatePath.('text.html');
 *
 * Copied from Gerrit's path-list-util.ts.
 */
export function truncatePath(path: string, threshold = 1) {
  const pathPieces = path.split('/');

  if (pathPieces.length <= threshold) {
    return path;
  }

  const index = pathPieces.length - threshold;
  // Character is an ellipsis.
  return `\u2026/${pathPieces.slice(index).join('/')}`;
}

/**
 * Encodes *parts* of a URL. See inline comments below for the details.
 * Note specifically that ? & = # are encoded. So this is very close to
 * encodeURIComponent() with some tweaks.
 *
 * Copied from Gerrit's url-util.ts.
 */
export function encodeURL(url: string): string {
  // gr-page decodes the entire URL, and then decodes once more the
  // individual regex matching groups. It uses `decodeURIComponent()`, which
  // will choke on singular `%` chars without two trailing digits. We prefer
  // to not double encode *everything* (just for readaiblity and simplicity),
  // but `%` *must* be double encoded.
  let output = url.replaceAll('%', '%25'); // `replaceAll` function requires `es2021` hence the `lib` section was added to `tsconfig.json`
  // `+` also requires double encoding, because `%2B` would be decoded to `+`
  // and then replaced by ` `.
  output = output.replaceAll('+', '%2B');

  // This escapes ALL characters EXCEPT:
  // A–Z a–z 0–9 - _ . ! ~ * ' ( )
  // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/encodeURIComponent
  output = encodeURIComponent(output);

  // If we would use `encodeURI()` instead of `encodeURIComponent()`, then we
  // would also NOT encode:
  // ; / ? : @ & = + $ , #
  //
  // That would be more readable, but for example ? and & have special meaning
  // in the URL, so they must be encoded. Let's discuss all these chars and
  // decide whether we have to encode them or not.
  //
  // ? & = # have to be encoded. Otherwise we might mess up the URL.
  //
  // : @ do not have to be encoded, because we are only dealing with path,
  // query and fragment of the URL, not with scheme, user, host, port.
  // For search queries it is much nicer to not encode those chars, think of
  // searching for `owner:spearce@spearce.org`.
  //
  // / does not have to be encoded, because we don't care about individual path
  // components. File path and repo names are so much nicer to read without /
  // being encoded!
  //
  // + must be encoded, because we want to use it instead of %20 for spaces, see
  // below.
  //
  // ; $ , probably don't have to be encoded, but we don't bother about them
  // much, so we don't reverse the encoding here, but we don't think it would
  // cause any harm, if we did.
  output = output.replace(/%3A/g, ':');
  output = output.replace(/%40/g, '@');
  output = output.replace(/%2F/g, '/');

  // gr-page replaces `+` by ` ` in addition to calling `decodeURIComponent()`.
  // So we can use `+` to increase readability.
  output = output.replace(/%20/g, '+');

  return output;
}

/**
 * Function to obtain the base URL.
 *
 * Copied from Gerrit's url-util.ts.
 */
export function getBaseUrl(): string {
  // window is not defined in service worker, therefore no CANONICAL_PATH
  if (typeof window === 'undefined') return '';
  return self.CANONICAL_PATH || '';
}
