// @ts-check

import { fail } from "node:assert";

/**
 * @param {number} n
 * @returns {number}
 */
export const hostFails = (n) => {
  switch (n) {
    case 1:
      throw new TypeError(`host bug ${n}`);
    case 2:
      return new Array(-n).length;
    default:
      throw `host bug ${n}`;
  }
};

/** Node's own test failure, as its assertions throw. */
export const hostFailsTest = () => {
  fail("host test failure");
};
