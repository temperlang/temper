// @ts-check

import { RoaringBitmap32, roaringLibraryInitialize } from "roaring-wasm";

await roaringLibraryInitialize();

/**
 * @returns {unknown}
 */
export const newBitsetConnected = () => {
  return new RoaringBitmap32();
};

/**
 * @param {RoaringBitmap32} bitset
 * @param {number} i
 */
export const bitsetAdd = (bitset, i) => {
  bitset.add(i);
};

/**
 * @param {RoaringBitmap32} bitset
 * @param {number} i
 * @returns {boolean}
 */
export const bitsetContains = (bitset, i) => {
  return bitset.contains(i);
};
