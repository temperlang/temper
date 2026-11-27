import { failureBase, listedGet, panic } from "@temperlang/core";

/**
 * @param {number} n
 * @returns {number}
 * @throws {NotPositive}
 */
function positive(n) {
  if (n <= 0) {
    throw new NotPositive(n);
  }
  return n;
}

/**
 * @param {readonly number[]} ls
 * @param {number} i
 * @returns {number}
 * @throws {NotPositive}
 */
function read(ls, i) {
  positive((i + 1) | 0);
  return listedGet(/** @type {number[]} */ (ls), i);
}

class Problem extends Error {}

failureBase(Problem);

class NotPositive extends Problem {
  /** @type {number} */
  #n;

  /** @param {number} n */
  constructor(n) {
    super();
    this.#n = n;
  }

  /** @returns {number} */
  get n() {
    return this.#n;
  }

  /** @returns {string} */
  get message() {
    return `not positive (n: ${this.#n})`;
  }
}

console.log("before");
/** @type {number} */
let r;
try {
  r = read(Object.freeze([1, 2]), 5);
} catch (e$) {
  if (!(e$ instanceof NotPositive)) throw e$;
  r = panic("panic", e$);
}
console.log(`after ${r}`);
