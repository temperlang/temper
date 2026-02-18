// @ts-check

export const regexCompiledFound = (_, compiled, text) => {
  compiled.lastIndex = 0;
  return compiled.test(text);
};

/**
 * @param {RegExp} compiled
 * @param {string} text
 * @param {number} begin
 */
export const regexCompiledFind = (_, compiled, text, begin, regexRefs) => {
  const match = regexCompiledFindEx(_, compiled, text, begin, regexRefs);
  if (match === undefined) {
    // We could just fail on `undefined.groups`, but that seems accidental.
    throw Error();
  }
  return match;
};

/**
 * @param {RegExp} compiled
 * @param {string} text
 * @param {number} begin
 */
function regexCompiledFindEx(_, compiled, text, begin, regexRefs) {
  compiled.lastIndex = begin;
  const match = compiled.exec(text);
  if (match === null) {
    return undefined;
  }
  const {
    // @ts-ignore
    groups: groupsMaybe,
    // @ts-ignore
    indices: { groups: indexGroupsMaybe },
  } = match;
  const groups = groupsMaybe || {};
  const indexGroups = indexGroupsMaybe || {};
  // Find the begin indices in code points for all matched groups.
  const Match = regexRefs.match.constructor;
  const Group = regexRefs.match.full.constructor;
  const resultGroups = new Map();
  const fullText = match[0];
  const fullBegin = match.index;
  const full = new Group(
    "full",
    fullText,
    fullBegin,
    fullBegin + fullText.length
  );
  for (const name of Object.keys(indexGroups)) {
    const indices = indexGroups[name];
    const text = groups[name];
    if (text === undefined) {
      continue;
    }
    const groupBegin = indices[0];
    resultGroups.set(
      name,
      new Group(name, text, groupBegin, groupBegin + text.length)
    );
  }
  return new Match(full, resultGroups);
}

/**
 * @param {RegExp} compiled
 * @param {string} text
 * @param {(groups: Map<string, any>) => string} format
 * @returns {string}
 */
export const regexCompiledReplace = (_, compiled, text, format, regexRefs) => {
  // Simple string replace doesn't provide all match group details if we want to
  // make our interface consistent, so we have to do this manually here.
  // The hope is that we can optimize a bunch out when we have compile-time
  // contant patterns and customized match result types.
  let result = "";
  let begin = 0;
  let keepBegin = begin;
  do {
    let match = regexCompiledFindEx(_, compiled, text, begin, regexRefs);
    if (match === undefined) {
      if (result == "") {
        // Manually handle no match case for our manual replace logic.
        return text;
      } else {
        result += text.substring(keepBegin);
        break;
      }
    }
    result += text.slice(keepBegin, match.full.begin);
    result += format(match);
    keepBegin = match.full.end;
    begin = Math.max(keepBegin, begin + 1);
  } while (begin <= text.length); // `<=` to see string end
  return result;
};

/**
 * @param {RegExp} compiled
 * @param {string} text
 * @returns {Readonly<string[]>}
 */
export const regexCompiledSplit = (_, compiled, text) => {
  return Object.freeze(text.split(compiled));
};

/**
 * @param {unknown} _
 * @param {string} formatted
 * @returns {RegExp}
 */
export const regexCompileFormatted = (_, formatted) => {
  if (formatted.includes("\\&")) {
    // std/regex escapes '&' because that's needed on Python, but
    // EcmaScript's builtin complains.
    formatted = formatted.replace(
      /\\+&/g,
      // If the length is even, then it is escaped because the number of
      // backslashes preceding is odd.
      (s) => (s.length & 1 ? s : s.substring(1))
    );
  }
  return new RegExp(formatted, "dgu"); // d:hasIndices, g:global, u:unicode
};

export const regexFormatterAdjustCodeSet = (self, codeSet, regexRefs) => {
  if (codeSet.negated) {
    let maxCode = codeSet.items.reduce(
      (maxCode, item) => Math.max(maxCode, self.maxCode(item)) ?? 0,
      0
    );
    if (maxCode < MIN_SUPPLEMENTAL_CP) {
      // Add a bonus explicit surrogate pair to encourage js code points.
      // Alternatively, we could construct the inverse positive set, but
      // this ends up looking a bit more like the provided form.
      if (codeSetBonus === null) {
        // Any surrogate pair will do.
        codeSetBonus = new regexRefs.codePoints.constructor("🌐");
      }
      return new regexRefs.orObject.constructor([
        codeSetBonus,
        new codeSet.constructor([codeSetBonus].concat(codeSet.items), true),
      ]);
    }
  }
  return codeSet;
};

/**
 * @param {unknown} _
 * @param {[string]} out
 * @param {number} code
 * @param {boolean} insideCodeSet
 */
export const regexFormatterPushCodeTo = (_, out, code, insideCodeSet) => {
  // Ignore insideCodeSet for now.
  // TODO(tjp, regex): Get fancier, including with work in Temper.
  out[0] += `\\u{${code.toString(16)}}`;
};

// Cached later for some approximate efficiency.
let codeSetBonus = null;

const MIN_SUPPLEMENTAL_CP = 0x10000;
