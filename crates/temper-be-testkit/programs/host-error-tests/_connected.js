// @ts-check

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

/**
 * A test failure as Node's assertions throw it: an `AssertionError`, which
 * the runtime knows by name (no `node:assert` import, so tsc needs no
 * Node types).
 */
export const hostFailsTest = () => {
  const failure = new Error("host test failure");
  failure.name = "AssertionError";
  throw failure;
};
