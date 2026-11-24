import { listedReduce, listedReduceFrom, modIntInt } from "@temperlang/core";

/**
 * @template T
 * @param {readonly T[]} items
 * @param {(arg0: T) => boolean} check
 * @returns {boolean}
 */
function all(items, check) {
  /**
   * @param {boolean} result
   * @param {T} it
   * @returns {boolean}
   */
  function fn(result, it) {
    if (result) {
      return check(it);
    } else {
      return false;
    }
  }
  return listedReduceFrom(/** @type {T[]} */ (items), true, fn);
}

/** @type {readonly number[]} */
const vals = Object.freeze([2, 3, 4]);
const sum = listedReduce(/** @type {number[]} */ (vals), (sum, n) => (sum + n) | 0);
const poorReverseJoin = listedReduceFrom(/** @type {number[]} */ (vals), "", (text, n) => `${n}${text}`);
console.log(`Reductions: ${sum} ${poorReverseJoin}`);
console.log(`All even: ${all(vals, (n) => modIntInt(n, 2) === 0)}`);
console.log(`All positive: ${all(vals, (n) => n > 0)}`);
