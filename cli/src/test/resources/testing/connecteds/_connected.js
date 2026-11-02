// @ts-check

import { RoaringBitmap32, roaringLibraryInitialize } from "roaring-wasm";

await roaringLibraryInitialize();

/** Temper's connected Bitset class, with the members Temper would generate. */
export class Bitset {
  #roaring = new RoaringBitmap32();

  /** @param {number} i */
  add(i) {
    this.#roaring.add(i);
  }

  /**
   * @param {number} i
   * @returns {boolean}
   */
  contains(i) {
    return this.#roaring.contains(i);
  }
}
