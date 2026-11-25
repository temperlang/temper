import * as _connected from "./_connected.js";
import { failureBase } from "@temperlang/core";

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
export let n;
try {
  n = hostLookup("z");
} catch (e) {
  if (!(e instanceof NotFound)) throw e;
  console.log(`caught ${e.key}`);
  n = -1;
}
/** @type {number} */
let t$0;
try {
  t$0 = hostLookup("a");
} catch (e$) {
  if (!(e$ instanceof NotFound)) throw e$;
  t$0 = 0;
}
console.log(`got ${n} ${t$0}`);
hostLookup("y");
console.log("after");
