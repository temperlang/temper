import { type } from "@temperlang/core";

/**
 * @param {number} x
 * @returns {number}
 */
export function ignore(x) {
  return (x + 1) | 0;
}

/** @returns {number} */
export function summary() {
  const s = new String_(3);
  const l = new List(4);
  return (((((((s.text + s.doubled) | 0) + l.n) | 0) + ignore(10)) | 0) + Boolean) | 0;
}

export class String_ extends type() {
  /** @type {number} */
  #text;

  /** @param {number} text */
  constructor(text) {
    super();
    this.#text = text;
  }

  /** @returns {number} */
  get text() {
    return this.#text;
  }

  /** @returns {number} */
  get doubled() {
    return Math.imul(this.#text, 2);
  }
}

export class List extends type() {
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

const Boolean = 7;
