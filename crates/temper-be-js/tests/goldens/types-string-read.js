import {
  isBubble,
  stringCountBetween,
  stringFromCodePoint,
  stringFromCodePoints,
  stringGet,
  stringHasAtLeast,
  stringNext,
  stringPrev,
  stringStep,
  stringToInt32,
} from "@temperlang/core";

/** @param {string} s */
function reportSliceInfo(s) {
  const start = 0;
  const end = s.length;
  const count = stringCountBetween(s, start, end);
  console.log(`Count of code-points for \`${s}\`: ${count}`);
  console.log(`Has at least 0: ${stringHasAtLeast(s, start, end, 0)}`);
  console.log(`Has at least 1: ${stringHasAtLeast(s, start, end, 1)}`);
  console.log(`Has at least 2: ${stringHasAtLeast(s, start, end, 2)}`);
  console.log(`Has at least 3: ${stringHasAtLeast(s, start, end, 3)}`);
  console.log(`Has at least 4: ${stringHasAtLeast(s, start, end, 4)}`);
  console.log(`Has at least 5: ${stringHasAtLeast(s, start, end, 5)}`);
  console.log(`Has at least 6: ${stringHasAtLeast(s, start, end, 6)}`);
  console.log(`Has at least 7: ${stringHasAtLeast(s, start, end, 7)}`);
  console.log(`Has at least 8: ${stringHasAtLeast(s, start, end, 8)}`);
  console.log("code-points");
  {
    let i = 0;
    while (s.length > i) {
      const cp = stringGet(s, i);
      i = stringNext(s, i);
      console.log(`  ${cp.toString(16)}`);
    }
  }
}

/** @param {string} message */
function compareEnds(message) {
  const start = 0;
  const end = message.length;
  console.log(`start < end? ${start - end < 0}`);
  console.log(`start > end? ${start - end > 0}`);
  console.log(`start == start? ${start === start}`);
  console.log(`end < end? ${end - end < 0}`);
  console.log(`end <= end? ${end - end <= 0}`);
}

/**
 * @param {number} code
 * @returns {string}
 */
function fromCodeNoInline(code) {
  console.log(`From 0x${code.toString(16)}`);
  return stringFromCodePoint(code);
}

/**
 * @param {readonly number[]} codes
 * @returns {string}
 */
function fromCodesNoInline(codes) {
  console.log("From codes");
  return stringFromCodePoints(/** @type {number[]} */ (codes));
}

/**
 * @param {string} string
 * @param {number | null} [radix]
 */
function checkParse(string, radix) {
  radix ??= null;
  /** @type {string} */
  let converted;
  try {
    converted = (radix != null ? stringToInt32(string, radix) : stringToInt32(string, null)).toString();
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    converted = "failed";
  }
  console.log(`Parse ${string} -> ${converted}`);
}

/**
 * @param {string} label
 * @param {string} haystack
 * @param {string} needle
 * @param {number | null} [start]
 * @returns {number}
 */
function reportFound(label, haystack, needle, start) {
  start ??= 0;
  const index = haystack.indexOf(needle, start);
  {
    if (index >= 0) {
      const before = haystack.substring(stringPrev(hay, start), /** @type {number} */ (/** @type {unknown} */ (index)));
      console.log(`${label}: ${before}...`);
      return stringNext(haystack, /** @type {number} */ (/** @type {unknown} */ (index)));
    } else {
      console.log(`${label}: (nope)`);
      return 0;
    }
  }
}

/**
 * @param {string} haystack
 * @param {string} needle
 * @param {number | null} [start]
 * @returns {number}
 */
function foundAt(haystack, needle, start) {
  start ??= 0;
  const index = haystack.indexOf(needle, start);
  {
    if (index >= 0) {
      return stringCountBetween(haystack, 0, /** @type {number} */ (/** @type {unknown} */ (index)));
    } else {
      return -1;
    }
  }
}

reportSliceInfo("κόσμε𐆊");
reportSliceInfo("Hi");
compareEnds("Hi");
console.log(stringFromCodePoint(127757));
console.log(fromCodeNoInline(127757));
/** @type {string} */
let t$0;
try {
  t$0 = fromCodeNoInline(1114112);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  t$0 = "nope";
}
console.log(t$0);
/** @type {string} */
let t$1;
try {
  t$1 = fromCodeNoInline(55296);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  t$1 = "no to surrogate";
}
console.log(t$1);
console.log(fromCodesNoInline(Object.freeze([128059, 8205, 10052, 65039])));
/** @type {string} */
let t$2;
try {
  t$2 = fromCodesNoInline(Object.freeze([55296, 56320]));
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  t$2 = "no to surrogates";
}
console.log(t$2);
checkParse("-1", null);
checkParse("2", null);
checkParse(" 2 ", null);
checkParse("2.0", null);
checkParse("2a", null);
checkParse("0x10", null);
checkParse("totally", null);
checkParse("2a", 16);
checkParse("20", 7);
checkParse("20", 17);
checkParse("20", 37);
{
  const s = "foo-bar";
  const after1_2 = stringNext(s, 0);
  const after2_2 = stringNext(s, after1_2);
  const after3 = stringNext(s, after2_2);
  const after4 = stringNext(s, after3);
  const after4b = stringStep(s, 0, 4);
  const before1 = stringPrev(s, after4b);
  const before3 = stringStep(s, before1, -2);
  const before3b = stringStep(s, before3, 0);
  const wayBefore = stringStep(s, before3, -100);
  const wayAfter = stringStep(s, before3, 100);
  console.log(`s[3:]  -> ${s.substring(after3, s.length)}`);
  console.log(`s[:3]  -> ${s.substring(0, after3)}`);
  console.log(`s[1:4] -> ${s.substring(after1_2, after4)}`);
  console.log(`s[:4]  -> ${s.substring(0, after4)}`);
  console.log(`s[1:4] -> ${s.substring(before3b, after4b)}`);
  console.log(`s[-many:many] -> ${s.substring(wayBefore, wayAfter)}`);
}
const hay = "It was the best of times, it was the worst of times, ...";
const needle = "time";
const after1 = reportFound("found 1", hay, needle);
const after2 = reportFound("found 2", hay, needle, after1);
reportFound("found 3", hay, needle, after2);
reportFound("hay 2", "time comes for us all", needle);
reportFound("hay 3", "in the nick of time", needle);
reportFound("hay 4", "my name is tim", needle);
reportFound("hay 5", "tim", needle);
const nulHay = "ab\x00#\x00";
console.log(`nul: ${foundAt(nulHay, "\x00")}`);
const afterNul = stringNext(nulHay, stringNext(nulHay, stringNext(nulHay, 0)));
console.log(`nul again: ${foundAt(nulHay, "\x00", afterNul)}`);
console.log(`soft hyphen: ${foundAt("ab\u00ad#", "\u00ad")}`);
console.log(`past soft hyphen: ${foundAt("ab\u00ad#", "b#")}`);
console.log(`zwj: ${foundAt("a\u200db", "ab")}`);
console.log(`composed: ${foundAt("é", "é")}`);
