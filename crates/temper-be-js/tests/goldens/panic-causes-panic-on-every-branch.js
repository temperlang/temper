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
let k;
try {
  k = positive(1);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  k = 0;
}
if (k > 0) {
  panic();
} else {
  try {
    positive(-1);
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    panic("panic", e$);
  }
  panic();
}
console.log("after");
