import * as _connected from "./_connected.js";
import { divIntInt, failureBase, isFailure, panic } from "@temperlang/core";
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
export let p;
try {
  const n = parseCount("two");
  const m = hostLookup("a");
  if (((n + m) | 0) > 2) {
    panic();
  }
  p = divIntInt(10, (n - m) | 0);
} catch (e$) {
  if (!isFailure(e$)) throw e$;
  p = -1;
}
console.log(`after ${p}`);
