import { bubble } from "@temperlang/core";

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
positive(-1);
console.log("after");
