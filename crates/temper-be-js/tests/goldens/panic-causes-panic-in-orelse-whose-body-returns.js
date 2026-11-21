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

/**
 * @param {number} n
 * @returns {number}
 */
function checked(n) {
  try {
    if (n === 0) {
      return 0;
    }
    positive(n);
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    panic("panic", e$);
  }
  return n;
}

console.log("before");
console.log(`after ${checked(-1)}`);
