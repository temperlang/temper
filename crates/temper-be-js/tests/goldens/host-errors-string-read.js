import { bubble, isBubble, stringGet } from "@temperlang/core";

/**
 * @param {number} i
 * @returns {number}
 */
function at(i) {
  if (i === 99) {
    bubble();
  }
  return i;
}

/**
 * @param {string} s
 * @param {number} i
 * @returns {number}
 */
function codeAt(s, i) {
  at(i);
  return stringGet(s, s.length);
}

console.log("reading");
/** @type {number} */
let r;
try {
  r = codeAt("ab", 0);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  r = -1;
}
console.log(`read the end ${r}`);
