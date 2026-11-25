import * as _connected from "./_connected.js";
import { failureBase, withCause } from "@temperlang/core";

/**
 * @param {string} key
 * @returns {number}
 * @throws {NotFound}
 */
export function hostLookup(key) {
  return _connected.hostLookup(key);
}

/**
 * @param {string} name
 * @returns {number}
 * @throws {LookupFailed}
 */
export function setting(name) {
  try {
    return hostLookup(name);
  } catch (failure) {
    if (!(failure instanceof NotFound)) throw failure;
    throw withCause(new LookupFailed(name), failure);
  }
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

export class LookupFailed extends HostFailuresError {
  /** @type {string} */
  #name_;

  /** @param {string} name */
  constructor(name) {
    super();
    this.#name_ = name;
  }

  /** @returns {string} */
  get name_() {
    return this.#name_;
  }

  /** @returns {string} */
  get message() {
    return `lookup failed (name: ${this.#name_})`;
  }
}

console.log("before");
setting("port");
console.log("after");
