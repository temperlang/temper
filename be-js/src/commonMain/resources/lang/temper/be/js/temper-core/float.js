// @ts-check

// Implements extension method Float64::near
import {bubble, INT32_MAX, INT32_MIN, INT64_MAX, INT64_MIN} from "./core.js";

/**
 * Implements extension method Float64::near
 * @param {number} x
 * @param {number} y
 * @param {number | null} [relTol]
 * @param {number | null} [absTol]
 * @returns {boolean}
 */
export const float64Near = (x, y, relTol, absTol) => {
  if (relTol == null) {
    relTol = 1e-9;
  }
  if (absTol == null) {
    absTol = 0;
  }
  const margin = Math.max(Math.max(Math.abs(x), Math.abs(y)) * relTol, absTol);
  return Math.abs(x - y) < margin;
}

/**
 * Implements extension method Float64::toInt32
 * @param {number} n
 * @returns {number}
 */
export const float64ToInt32 = (n) => {
  const i = float64ToInt32Unsafe(n);
  if (Math.abs(n - i) < 1) {
    return i;
  } else {
    bubble();
  }
}

/**
 * Implements extension method Float64::toInt32Unsafe
 * @param {number} n
 * @returns {number}
 */
export const float64ToInt32Unsafe = (n) => {
  // We are free to do whatever with NaN here.
  return isNaN(n)
    ? 0
    : Math.max(
      INT32_MIN,
      Math.min(Math.trunc(n), INT32_MAX)
    );
}

/**
 * Implements extension method Float64::toInt64
 * @param {number} n
 * @returns {bigint}
 */
export const float64ToInt64 = (n) => {
  // Also blocks NaNs.
  if (!(n >= Number.MIN_SAFE_INTEGER && n <= Number.MAX_SAFE_INTEGER)) {
    bubble();
  }
  return float64ToInt64Unsafe(n);
}

/**
 * Implements extension method Float64::toInt64Unsafe
 * @param {number} n
 * @returns {bigint}
 */
export const float64ToInt64Unsafe = (n) => {
  // We are free to do whatever with NaN here.
  return isNaN(n)
    ? 0n
    : BigInt(Math.max(
      // Avoid converting giant numbers to bigint.
      Number(INT64_MIN),
      Math.min(Math.trunc(n), Number(INT64_MAX)),
    ));
}

/**
 * Implements extension method Float64::toString
 * @param {number} n
 * @returns
 */
export const float64ToString = (n) => {
  // TODO(mikesamuel, issue#579): need functional test to nail down
  // double formatting thresholds.
  if (n === 0) {
    return Object.is(n, -0) ? "-0.0" : "0.0";
  } else {
    let result = n.toString();
    // Rely on eagerness and js number formatting rules here.
    const groups = /(-?[0-9]+)(\.[0-9]+)?(.+)?/.exec(result);
    if (groups === null) {
      return result;
    } else {
      // Guarantee a decimal point for floats.
      return `${groups[1]}${groups[2] || ".0"}${groups[3] || ""}`;
    }
  }
}
