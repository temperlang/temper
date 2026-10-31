// @ts-check

/** Temper's connected Counter class, with the members Temper would generate. */
export class Counter {
  #total = 0;

  /** @param {number} i */
  add(i) {
    this.#total += i;
  }

  /** @returns {number} */
  total() {
    return this.#total;
  }
}
