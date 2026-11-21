import { bubble, isBubble, listedGet, panic } from "@temperlang/core";

/**
 * @param {number} n
 * @returns {number}
 */
function positive(n) {
  if (n <= 0) {
    bubble();
  }
  return n;
}

/**
 * @param {readonly number[]} ls
 * @param {number} i
 * @returns {number}
 */
function read(ls, i) {
  positive((i + 1) | 0);
  return listedGet(/** @type {number[]} */ (ls), i);
}

console.log("before");
/** @type {number} */
let r;
try {
  r = read(Object.freeze([1, 2]), 5);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  r = panic("panic", e$);
}
console.log(`after ${r}`);
