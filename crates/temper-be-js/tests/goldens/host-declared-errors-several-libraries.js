import * as _connected from "./_connected.js";
import {
  divIntInt,
  failureBase,
  failureCause,
  isFailure,
  withCause,
} from "@temperlang/core";
import { parseCount } from "count-errors";

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
 * @param {number} n
 * @returns {number}
 * @throws {Zero}
 */
export function nonzero(n) {
  if (n === 0) {
    throw new Zero();
  } else {
    return n;
  }
}

/**
 * @param {string} key
 * @param {string} count
 * @returns {number}
 */
export function ratio(key, count) {
  try {
    return divIntInt(hostLookup(key), nonzero(parseCount(count)));
  } catch (e$) {
    if (!isFailure(e$)) throw e$;
    return -9;
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

export class Unreadable extends HostDeclaredErrorsError {
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

export class Problem extends Error {}

failureBase(Problem);

export class Zero extends Problem {
  /** @returns {string} */
  get message() {
    return "zero";
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
