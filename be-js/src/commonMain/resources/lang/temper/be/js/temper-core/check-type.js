
// Export runtime value type checks used for safe casting
import {bubble} from "./core.js";

/**
 * @typedef { "bigint" | "boolean" | "number" | "object" | "string" | "symbol" | "undefined" } TypeOf
 */

/**
 * @template T
 * @param {any} x
 * @param {new(...args: any[]) => T} typeRequirement
 * @returns {T}
 */
export const requireInstanceOf = (x, typeRequirement) => {
  if (!(x instanceof typeRequirement)) {
    bubble();
  }
  return x;
};

/**
 * @template T
 * @param {T?} x
 * @returns {T}
 */
export const requireNotNull = (x) =>
  x == null ? bubble() : x

/**
 * @template X, Y
 * @param {X} x
 * @param {Y} y
 * @returns {boolean}
 */
export const requireSame = (x, y) => {
  if (x !== y) {
    bubble();
  }
  return x;
};

/**
 * @template T
 * @param {T} x
 * @param {TypeOf} typeOfString
 * @returns {T}
 */
export const requireTypeOf = (x, typeOfString) => {
  if (typeof x !== typeOfString) {
    bubble();
  }
  return x;
};
