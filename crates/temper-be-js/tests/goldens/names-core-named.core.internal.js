import { divIntInt } from "@temperlang/core";

/**
 * @param {string} who
 * @returns {string}
 */
export function greet(who) {
  return `hello from ${who}`;
}

/**
 * @param {number} n
 * @param {number} d
 * @returns {number}
 */
export function share(n, d) {
  try {
    return divIntInt(n, d);
  } catch {
    return 0;
  }
}
