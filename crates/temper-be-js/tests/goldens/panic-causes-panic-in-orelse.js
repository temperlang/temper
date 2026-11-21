import { bubble, isBubble, panic } from "@temperlang/core";

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

console.log("before");
/** @type {number} */
let p;
try {
  p = positive(-1);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  p = panic("panic", e$);
}
console.log(`after ${p}`);
