import { listedGet } from "@temperlang/core";

/**
 * @param {readonly string[]} listed
 * @param {number} i
 * @returns {string}
 */
function at(listed, i) {
  return listedGet(/** @type {string[]} */ (listed), i);
}

/** @type {readonly string[]} */
const letters = Object.freeze(["a", "b", "c"]);
console.log(at(letters, 2));
console.log("before");
console.log(at(letters, 3));
console.log("not reached");
