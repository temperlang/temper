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
let k;
try {
  k = positive(1);
} catch (e$) {
  if (!(e$ instanceof NotPositive)) throw e$;
  k = 0;
}
if (k > 0) {
  panic();
} else {
  try {
    positive(-1);
  } catch (e$) {
    if (!(e$ instanceof NotPositive)) throw e$;
    panic("panic", e$);
  }
  panic();
}
console.log("after");
