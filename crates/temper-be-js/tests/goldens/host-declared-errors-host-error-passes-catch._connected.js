// @ts-check

// A specification: `NotFound` is the library's own error class, from
// its main module, as the JS backend names it.
import { NotFound } from "./host_declared_errors_host_error_passes_catch.js";

/**
 * @param {string} key
 * @returns {number}
 * @throws {NotFound}
 */
export const hostLookup = (key) => {
  if (key === "bug") {
    throw new TypeError("host bug");
  }
  if (key === "a") {
    return 1;
  }
  throw new NotFound(key);
};
