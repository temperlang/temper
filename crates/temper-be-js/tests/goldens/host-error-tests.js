import * as _connected from "./_connected.js";
import { failureBase } from "@temperlang/core";

/**
 * @param {number} n
 * @returns {number}
 * @throws {Oops}
 */
export function hostFails(n) {
  return _connected.hostFails(n);
}

export function hostFailsTest() {
  _connected.hostFailsTest();
}

export class OopsError extends Error {}

failureBase(OopsError);

export class Oops extends OopsError {
  /** @returns {string} */
  get message() {
    return "oops";
  }
}
