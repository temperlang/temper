import { type } from "@temperlang/core";

/**
 * @param {number} x
 * @returns {number}
 */
export function twice(x) {
  return Math.imul(x, 2);
}

/** @template T */
export class Box extends type() {
  /** @type {T} */
  #value;

  /** @param {T} value */
  constructor(value) {
    super();
    this.#value = value;
  }

  /** @returns {T} */
  get value() {
    return this.#value;
  }
}

export class Shape extends type() {
  /** @type {() => number} */
  area = /** @type {any} */ (undefined);
}

export class Square extends type(Shape) {
  /** @type {number} */
  #side;

  /** @param {number} side */
  constructor(side) {
    super();
    this.#side = side;
  }

  /** @returns {number} */
  get side() {
    return this.#side;
  }

  /** @returns {number} */
  area() {
    return Math.imul(this.#side, this.#side);
  }

  /**
   * @param {number} side
   * @returns {Square}
   */
  static sized(side) {
    return new Square(side);
  }
}

export const unit = 1;
