import { float64ToString, type } from "@temperlang/core";

/**
 * @param {Vec2} vec
 * @returns {number}
 */
export function normSquared(vec) {
  return vec.x * vec.x + vec.y * vec.y;
}

export class Vec2 extends type() {
  /** @type {number} */
  #x;
  /** @type {number} */
  #y;

  /**
   * @param {{ x: number, y: number }} props
   * @returns {Vec2}
   */
  static new(props) {
    return new Vec2(props.x, props.y);
  }

  /**
   * @param {number} x
   * @param {number} y
   */
  constructor(x, y) {
    super();
    this.#x = x;
    this.#y = y;
  }

  /** @returns {number} */
  get x() {
    return this.#x;
  }

  /** @returns {number} */
  get y() {
    return this.#y;
  }

  /** @returns {string} */
  toString() {
    return `(${float64ToString(this.#x)}, ${float64ToString(this.#y)}) size squared ${float64ToString(normSquared(this))}`;
  }
}

class KeptForHost extends type() {
  /** @type {number} */
  #n;

  /** @param {number} n */
  constructor(n) {
    super();
    this.#n = n;
  }

  /** @returns {number} */
  get n() {
    return this.#n;
  }
}

/** @type {readonly Vec2[]} */
export const unitVecs = Object.freeze([new Vec2(1.0, 0.0), new Vec2(0.0, 1.0)]);
