// @ts-check
import { Hidden } from "./work.internal.js";
import { Support } from "./_support.js";

/**
 * @param {number} i
 * @param {number} j
 * @param {number} bonus
 */
export const sum = (i, j, bonus) => {
  return i + j + bonus;
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
