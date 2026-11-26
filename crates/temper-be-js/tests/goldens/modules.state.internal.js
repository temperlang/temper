import { type } from "@temperlang/core";

export class State extends type() {
  /** @type {number} */
  #id;

  /** @param {number} id */
  constructor(id) {
    super();
    this.#id = id;
  }

  /** @returns {number} */
  get id() {
    return this.#id;
  }
}

export class Engine extends type() {
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

export class Parts extends type() {
  /** @type {number} */
  #count;

  /** @param {number} count */
  constructor(count) {
    super();
    this.#count = count;
  }

  /** @returns {number} */
  get count() {
    return this.#count;
  }
}

export class Spot extends type() {
  /** @type {number} */
  #x;
  /** @type {number} */
  #y;

  /**
   * @param {{ x: number, y: number }} props
   * @returns {Spot}
   */
  static new(props) {
    return new Spot(props.x, props.y);
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
}

export const firstId = 1;
