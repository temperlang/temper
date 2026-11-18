import { type } from "@temperlang/core";

/**
 * @param {number} n
 * @returns {number}
 */
export function lambda(n) {
  return (n + 1) | 0;
}

export class Checked extends type() {
  /** @type {number} */
  #end;

  /** @param {number} end */
  constructor(end) {
    super();
    this.#end = end;
  }

  /** @returns {number} */
  get end() {
    return this.#end;
  }

  /** @returns {number} */
  delete_() {
    return Math.imul(this.#end, 2);
  }

  /** @returns {number} */
  get local() {
    return (this.#end + native) | 0;
  }
}

export class Builtins extends type() {
  /** @type {number} */
  #hash;

  /** @param {number} hash */
  constructor(hash) {
    super();
    this.#hash = hash;
  }

  /** @returns {number} */
  get hash() {
    return this.#hash;
  }

  /**
   * @param {number} i
   * @returns {number}
   */
  get(i) {
    return (this.#hash + i) | 0;
  }

  /**
   * @param {number} i
   * @returns {number}
   */
  set(i) {
    return Math.imul(i, 2);
  }

  /**
   * @param {number} i
   * @returns {number}
   */
  map(i) {
    return (i + 1) | 0;
  }

  /** @returns {string} */
  format() {
    return `#${this.#hash}`;
  }

  /** @returns {number} */
  get int() {
    return Math.imul(this.#hash, 10);
  }

  /** @returns {number} */
  later() {
    return (this.int + 1) | 0;
  }

  /** @returns {number} */
  init() {
    return (this.#hash + 3) | 0;
  }

  /** @returns {string} */
  print() {
    return `print ${this.init()}`;
  }
}

export const native = 2;
