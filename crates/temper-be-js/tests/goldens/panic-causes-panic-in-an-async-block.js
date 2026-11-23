import { adaptAwaiter, listedGet, runAsync } from "@temperlang/core";

/**
 * @param {readonly number[]} ls
 * @returns {number}
 */
function third(ls) {
  return listedGet(/** @type {number[]} */ (ls), 2);
}

console.log("before");

/**
 * @param {(p: Promise<unknown>) => void} doAwait
 * @returns {Generator<any>}
 */
function* fn(doAwait) {
  console.log("in async");
  third(Object.freeze([1, 2]));
}

runAsync(adaptAwaiter(fn));
