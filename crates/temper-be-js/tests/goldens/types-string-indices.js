import {
  isBubble,
  listedGet,
  stringFromCodePoint,
  stringGet,
  stringNext,
} from "@temperlang/core";

/**
 * @param {string} s
 * @param {number} c
 * @returns {number}
 */
function indexOfChar(s, c) {
  let i = 0;
  while (s.length > i) {
    if (stringGet(s, i) === c) {
      return i;
    }
    i = stringNext(s, i);
  }
  return -1;
}

/**
 * @param {string} s
 * @param {number} a
 * @param {number} b
 */
function comparePositions(s, a, b) {
  const delta = indexOfChar(s, a) - indexOfChar(s, b);
  const relPos = delta < 0 ? "before" : delta === 0 ? "at" : "after";
  /** @type {string} */
  let t$0;
  try {
    t$0 = stringFromCodePoint(a);
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    t$0 = "?";
  }
  /** @type {string} */
  let t$1;
  try {
    t$1 = stringFromCodePoint(b);
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    t$1 = "?";
  }
  console.log(`'${t$0}' is ${relPos} '${t$1}' in "${s}".`);
}

/**
 * @param {string} s
 * @param {number} c
 * @returns {boolean}
 */
function has0(s, c) {
  return indexOfChar(s, c) >= 0;
}

/**
 * @param {string} s
 * @param {number} c
 * @returns {boolean}
 */
function has1(s, c) {
  const i = indexOfChar(s, c);
  return i >= 0;
}

/**
 * @param {string} s
 * @param {number} c
 * @returns {boolean}
 */
function has2(s, c) {
  const i = indexOfChar(s, c);
  return i >= 0;
}

/**
 * @param {string} s
 * @param {number} c
 * @returns {boolean}
 */
function has3(s, c) {
  const i = indexOfChar(s, c);
  return i >= 0;
}

/**
 * @param {string} s
 * @param {number} c
 * @returns {boolean}
 */
function has4(s, c) {
  const i = indexOfChar(s, c);
  return i >= 0;
}

comparePositions("cookie", 99, 105);
comparePositions("cookie", 107, 111);
comparePositions("cookie", 107, 107);
comparePositions("cookie", 120, 99);
comparePositions("cookie", 101, 120);
comparePositions("cookie", 120, 121);
const s = "cookie";
{
  /** @type {readonly number[]} */
  const list = Object.freeze([111, 105, 122]);
  const length = list.length;
  let i = 0;
  while (i < length) {
    const c = listedGet(/** @type {number[]} */ (list), i);
    i = (i + 1) | 0;
    /** @type {string} */
    let t$2;
    try {
      t$2 = stringFromCodePoint(c);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      t$2 = "?";
    }
    console.log(`'${t$2}' in "${s}": ${has0(s, c) ? "y" : "n"}, ${has1(s, c) ? "y" : "n"}, ${has2(s, c) ? "y" : "n"}, ${has3(s, c) ? "y" : "n"}, ${has4(s, c) ? "y" : "n"}`);
  }
}
{
  const stringBuilder = [""];
  stringBuilder[0] += "Hello";
  stringBuilder[0] += ",";
  const afterComma = stringBuilder[0].length;
  stringBuilder[0] += " World!";
  const str = stringBuilder[0];
  console.log(str.substring(0, afterComma));
  console.log(str.substring(afterComma, str.length));
}
