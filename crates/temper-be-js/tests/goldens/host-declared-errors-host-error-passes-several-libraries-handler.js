import * as _connected from "./_connected.js";
import { failureBase, isFailure } from "@temperlang/core";
import { parseCount } from "count-errors";

/**
 * @param {string} key
 * @returns {number}
 * @throws {NotFound}
 */
export function hostLookup(key) {
  return _connected.hostLookup(key);
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
export let h;
try {
  h = (parseCount("two") + hostLookup("bug")) | 0;
} catch (e) {
  if (!isFailure(e)) throw e;
  console.log(`caught ${e.message}`);
  h = -1;
}
console.log(`after ${h}`);
