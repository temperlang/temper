// @ts-check
import { Hidden, sumOf3 } from "./work.internal.js";
import { Support } from "./_support.js";

/**
 * @param {number} i
 * @param {number} j
 * @param {number} bonus
 */
export const sum = (i, j, bonus) => {
  return sumOf3(i, j, bonus);
};

/**
 * @param {Hidden} hidden
 * @param {number} j
 */
export const prod = (hidden, j) => {
  return new Support().prod(hidden.i, j);
};

/**
 * @param {string | null} s
 * @returns {number}
 */
export const length = (s) => {
  return s?.length ?? -1;
};
