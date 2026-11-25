import * as _connected from "./_connected.js";
import {
  divIntInt,
  failureBase,
  isBubble,
  isFailure,
  panic,
} from "@temperlang/core";
import { parseCount } from "count-failures";

/**
 * @param {string} key
 * @returns {number}
 * @throws {NotFound}
 */
export function hostLookup(key) {
  return _connected.hostLookup(key);
}

export class HostFailuresError extends Error {}

failureBase(HostFailuresError);

export class NotFound extends HostFailuresError {
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
  if (!(isBubble(e$) || isFailure(e$))) throw e$;
  p = -1;
}
console.log(`after ${p}`);
