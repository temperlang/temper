import * as _connected from "./_connected.js";
import { failureBase, panic } from "@temperlang/core";

/**
 * @param {number} n
 * @returns {number}
 * @throws {Oops}
 */
export function hostFails(n) {
  return _connected.hostFails(n);
}

export class OopsError extends Error {}

failureBase(OopsError);

export class Oops extends OopsError {
  /** @returns {string} */
  get message() {
    return "oops";
  }
}

console.log("before");
/** @type {number} */
export let h;
try {
  h = hostFails(1);
} catch (e$) {
  if (!(e$ instanceof Oops)) throw e$;
  h = panic("panic", e$);
}
console.log(`after ${h}`);
