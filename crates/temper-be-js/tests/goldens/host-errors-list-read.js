import { bubble, isBubble, listedGet } from "@temperlang/core";

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
function read(ls, i) {
  return listedGet(/** @type {number[]} */ (ls), at(i));
}

console.log("reading");
/** @type {number} */
let r;
try {
  r = read(Object.freeze([1, 2]), 5);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  r = -1;
}
console.log(`read past the end ${r}`);
