// @ts-check

// A specification: `NotFound` is the library's own failure class, from
// its main module, as the JS backend names it.
import { NotFound } from "./host_failures_panic_passes_several_libraries_handler.js";

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
