import { panic, type } from "@temperlang/core";

/**
 * @param {number} x
 * @returns {number}
 */
export function good(x) {
  return (x + 1) | 0;
}

/**
 * @param {number} x
 * @returns {number}
 */
function mismatched(x) {
  const s = x;
  return 1;
}

/**
 * @param {string} s
 * @returns {number}
 */
function misspelled(s) {
  return panic();
}

/**
 * @param {unknown} x
 * @returns {number}
 */
function unknownType(x) {
  return 1;
}

/**
 * @param {number} x
 * @returns {number}
 */
function afterUnclosed(x) {
  return Math.imul(x, 3);
}

/**
 * @param {number} x
 * @returns {string}
 */
function stored(x) {
  const s = x;
  return `${s}!`;
}

/** @returns {number} */
function returned() {
  return "text";
}

/**
 * @param {number} x
 * @returns {number}
 */
function passedOn(x) {
  return x;
}

class Box extends type() {
  /** @type {number} */
  #value;

  /** @param {number} value */
  constructor(value) {
    super();
    this.#value = value;
  }

  /** @returns {number} */
  get value() {
    return this.#value;
  }

  /** @returns {number} */
  get() {
    return this.#value;
  }

  /** @returns {string} */
  bad() {
    return this.#value;
  }
}

const box = new Box(3);
console.log(`${good(1)} ${unknownType(1)} ${afterUnclosed(2)} ${box.get()}`);
console.log(`${mismatched(1)} ${stored(1)} ${returned()} ${passedOn("in")} ${box.bad()}`);
misspelled("a");
console.log("after");
