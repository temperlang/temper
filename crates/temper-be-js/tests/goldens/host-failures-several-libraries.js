import * as _connected from "./_connected.js";
import {
  divIntInt,
  failureBase,
  failureCause,
  isBubble,
  isFailure,
  withCause,
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

/**
 * @param {string} key
 * @param {string} count
 * @returns {number}
 */
export function total(key, count) {
  try {
    return (hostLookup(key) + parseCount(count)) | 0;
  } catch (e) {
    if (!isFailure(e)) throw e;
    console.log(`caught ${e.message}`);
    return e instanceof NotFound ? -1 : -2;
  }
}

/**
 * @param {string} key
 * @param {string} count
 * @returns {number}
 * @throws {Unreadable}
 */
export function reading(key, count) {
  try {
    return (hostLookup(key) + parseCount(count)) | 0;
  } catch (failure) {
    if (!isFailure(failure)) throw failure;
    throw withCause(new Unreadable(key), failure);
  }
}

/**
 * @param {string} key
 * @param {string} count
 * @returns {number}
 */
export function ratio(key, count) {
  try {
    return divIntInt(hostLookup(key), parseCount(count));
  } catch (e$) {
    if (!(isBubble(e$) || isFailure(e$))) throw e$;
    return -9;
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

export class Unreadable extends HostFailuresError {
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
    return `unreadable (key: ${this.#key})`;
  }
}

console.log("before");
console.log(`${total("a", "two")} ${total("z", "two")} ${total("a", "x")}`);
/** @type {number} */
export let r;
try {
  r = reading("a", "x");
} catch (e) {
  if (!(e instanceof Unreadable)) throw e;
  /** @type {string | null} */
  let t$0;
  {
    const subject2 = failureCause(e);
    t$0 = subject2 == null ? null : subject2.message;
  }
  console.log(`${e.message}, caused by ${t$0 ?? "nothing"}`);
  r = 0;
}
console.log(`${r} ${ratio("a", "two")} ${ratio("a", "zero")} ${ratio("a", "x")} ${ratio("z", "two")}`);
parseCount("x");
console.log("after");
