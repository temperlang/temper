import { divIntInt, type } from "@temperlang/core";

/**
 * @param {number} n
 * @param {number} d
 * @returns {number}
 */
export function share(n, d) {
  try {
    return divIntInt(n, d);
  } catch {
    return 0;
  }
}

/**
 * @param {number} n
 * @param {number} d
 * @returns {Ratio | null}
 */
export function ratioOf(n, d) {
  if (d === 0) {
    return null;
  } else {
    return new Ratio(n, d);
  }
}

export class Ratio extends type() {
  /** @type {number} */
  #n;
  /** @type {number} */
  #d;

  /**
   * @param {{ n: number, d: number }} props
   * @returns {Ratio}
   */
  static new(props) {
    return new Ratio(props.n, props.d);
  }

  /**
   * @param {number} n
   * @param {number} d
   */
  constructor(n, d) {
    super();
    this.#n = n;
    this.#d = d;
  }

  /** @returns {number} */
  get n() {
    return this.#n;
  }

  /** @returns {number} */
  get d() {
    return this.#d;
  }

  /**
   * @param {Ratio} other
   * @returns {boolean}
   */
  eq(other) {
    return Math.imul(this.#n, other.#d) === Math.imul(other.#n, this.#d);
  }
}
