import { bubble, isBubble, listedGet, panic } from "@temperlang/core";

/**
 * @param {readonly number[]} ls
 * @param {number} i
 * @returns {number}
 */
function read(ls, i) {
  if (i < 0) {
    bubble();
  }
  return listedGet(/** @type {number[]} */ (ls), i);
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
panic();
