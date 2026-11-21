import { panic } from "@temperlang/core";

/**
 * @param {number} n
 * @returns {number}
 */
function boom(n) {
  if (n > 0) {
    panic();
  }
  return n;
}

console.log("before");
boom(1);
console.log("after");
