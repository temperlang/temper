// @ts-check

/**
 * @param {number} n
 * @returns {number}
 */
export const hostFails = (n) => {
  throw new TypeError(`host bug ${n}`);
};
