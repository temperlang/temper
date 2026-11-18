import * as _connected from "./_connected.js";
import { type } from "@temperlang/core";

/**
 * @param {number} a
 * @param {number} b
 * @param {number} c
 * @returns {number}
 */
export function sumOf3(a, b, c) {
  return (((a + b) | 0) + c) | 0;
}

/**
 * @param {number} i
 * @param {number} j
 * @param {number | null} [bonus]
 * @returns {number}
 */
export function sum(i, j, bonus) {
  bonus ??= 0;
  return _connected.sum(i, j, bonus);
}

/**
 * @param {string | null} [string]
 * @returns {number}
 */
export function length(string) {
  string ??= null;
  return _connected.length(string ?? null);
}

/**
 * @param {number} i
 * @returns {number}
 */
export function twice(i) {
  return _connected.twice(i);
}

/**
 * @param {Hidden} hidden
 * @param {number} j
 * @returns {number}
 */
export function prod(hidden, j) {
  return _connected.prod(hidden, j);
}

export class Hidden extends type() {
  /** @type {number} */
  #i;

  /** @param {number} i */
  constructor(i) {
    super();
    this.#i = i;
  }

  /** @returns {number} */
  get i() {
    return this.#i;
  }
}

export class Hider extends type() {
  /** @type {Hidden} */
  #hidden;

  /** @param {number} i */
  constructor(i) {
    super();
    this.#hidden = new Hidden(i);
  }

  /**
   * @param {number} j
   * @returns {number}
   */
  plus(j) {
    return sum(this.#hidden.i, j);
  }

  /**
   * @param {number} j
   * @returns {number}
   */
  times(j) {
    return prod(this.#hidden, j);
  }
}

console.log(`sum(1, 2): ${sum(1, 2)}`);
console.log(`length "" vs null vs (): ${length("")} vs ${length(null)} vs ${length()}`);
console.log(`twice(5): ${twice(5)}`);
export const isConnected = sum(1, 2) === 3;
if (isConnected) {
  console.log(`prod(new Hidden(1), 2): ${prod(new Hidden(1), 2)}`);
}
export const hider = new Hider(3);
console.log(`new Hider(3).plus(4): ${hider.plus(4)}`);
if (isConnected) {
  console.log(`new Hider(3).times(4): ${hider.times(4)}`);
}
