
// Export runtime value type checks used for safe casting, which panic
// where a cast fails
import {panic} from "./core.js";

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
    panic("failed cast");
  }
  return x;
};

/**
 * @template T
 * @param {any} x
 * @returns {Array<T> | ReadonlyArray<T>}
 */
export const requireIsArray = (x) => {
  if (!Array.isArray(x)) {
    panic("failed cast");
  }
  return x;
};

/**
 * `NonNullable`, so a value TypeScript has narrowed to `null` gives
 * `never`, not `null`.
 * @template T
 * @param {T} x
 * @returns {NonNullable<T>}
 */
export const requireNotNull = (x) =>
  x == null ? panic("failed cast") : x

/**
 * @template X, Y
 * @param {X} x
 * @param {Y} y
 * @returns {boolean}
 */
export const requireSame = (x, y) => {
  if (x !== y) {
    panic("failed cast");
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
    panic("failed cast");
  }
  return x;
};
