import { sum } from "./modules.js";

/**
 * @param {number} n
 * @returns {number}
 */
export function sumTwice(n) {
  return Math.imul(sum(n), 2);
}
