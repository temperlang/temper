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

/**
 * @param {number} i
 * @returns {number}
 */
export const twice = (i) => {
  return i * 2;
};

/** Temper's connected Counter class, with the members Temper would generate. */
export class Counter {
  #total = 0;

  /** @param {number} i */
  add(i) {
    this.#total += i;
  }

  /** @returns {number} */
  total() {
    return this.#total;
  }
}
