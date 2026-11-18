import { greet } from "./helpers/deep.js";

/**
 * @param {number} x
 * @returns {number}
 */
export function twice(x) {
  return Math.imul(x, 2);
}

/**
 * @param {number} x
 * @returns {number}
 */
export function intDoubled(x) {
  return twice(x);
}

/** @returns {number} */
export function bump() {
  calls = (calls + 1) | 0;
  return calls;
}

export const base = 10;
export let calls = 0;
console.log(greet("helpers"));
