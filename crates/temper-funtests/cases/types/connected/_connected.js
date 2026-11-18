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

/** Temper's connected Doubler class, with the members Temper would generate. */
export class Doubler {
  /**
   * @param {number} i
   * @returns {number}
   */
  twice(i) {
    return i * 2;
  }
}
