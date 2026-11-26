import { listedReduce } from "@temperlang/core";

/**
 * @param {readonly number[]} values
 * @returns {number}
 */
function sum(values) {
  return listedReduce(/** @type {number[]} */ (values), (a, b) => (a + b) | 0);
}

console.log(sum(Object.freeze([1, 2, 3])).toString());
console.log("before");
console.log(sum(Object.freeze([])).toString());
console.log("not reached");
