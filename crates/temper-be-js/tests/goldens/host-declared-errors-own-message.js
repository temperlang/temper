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

/**
 * @param {number} days
 * @throws {Expired}
 */
export function check(days) {
  if (days > 0) {
    throw new Expired(days);
  }
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

export class Expired extends HostDeclaredErrorsError {
  /** @type {number} */
  #days;

  /** @param {number} days */
  constructor(days) {
    super();
    this.#days = days;
  }

  /** @returns {number} */
  get days() {
    return this.#days;
  }

  /** @returns {string} */
  get message() {
    return `expired ${this.#days} days ago`;
  }
}

console.log("before");
check(0);
check(3);
console.log("after");
