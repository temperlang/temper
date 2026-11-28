import * as _connected from "./_connected.js";
import { failureBase, panic } from "@temperlang/core";

/**
 * @param {string} key
 * @returns {number}
 * @throws {NotFound}
 */
export function hostLookup(key) {
  return _connected.hostLookup(key);
}

/**
 * @param {number} n
 * @returns {number}
 * @throws {NotFound}
 */
export function risky(n) {
  if (n > 1) {
    panic();
  }
  if (n > 0) {
    throw new NotFound("n");
  }
  return n;
}

export class HostDeclaredErrorsError extends Error {}

failureBase(HostDeclaredErrorsError);

export class NotFound extends HostDeclaredErrorsError {
  /** @type {string} */
  #key;

  /** @param {string} key */
  constructor(key) {
    super();
    this.#key = key;
  }

  /** @returns {string} */
  get key() {
    return this.#key;
  }

  /** @returns {string} */
  get message() {
    return `not found (key: ${this.#key})`;
  }
}

console.log("before");
/** @type {number} */
export let r;
try {
  r = risky(2);
} catch (e$) {
  if (!(e$ instanceof NotFound)) throw e$;
  r = -1;
}
console.log(`after ${r}`);
