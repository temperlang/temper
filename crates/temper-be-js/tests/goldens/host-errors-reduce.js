import { bubble, isBubble, listedReduce } from "@temperlang/core";

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
 * @param {readonly number[]} ls
 * @param {number} i
 * @returns {number}
 */
function sum(ls, i) {
  at(i);
  return listedReduce(/** @type {number[]} */ (ls), (a, b) => (a + b) | 0);
}

console.log("reading");
/** @type {number} */
let r;
try {
  r = sum(Object.freeze([]), 0);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  r = -1;
}
console.log(`reduced empty ${r}`);
