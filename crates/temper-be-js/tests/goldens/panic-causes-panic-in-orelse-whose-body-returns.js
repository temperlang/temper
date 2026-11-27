import { failureBase, panic } from "@temperlang/core";

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
 * @param {number} n
 * @returns {number}
 */
function checked(n) {
  try {
    const p = positive(n);
    if (p === 1) {
      return 0;
    }
  } catch (e$) {
    if (!(e$ instanceof NotPositive)) throw e$;
    panic("panic", e$);
  }
  return n;
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
console.log(`after ${checked(-1)}`);
