import { listedGet, type } from "@temperlang/core";

/**
 * @param {number} n
 * @returns {string}
 */
function whatever(n) {
  if (n === 0) {
    console.log("0");
  }
  return "undefined";
}

/**
 * @template T
 * @param {readonly T[]} things
 * @returns {T}
 */
function lastOf(things) {
  const thing = listedGet(/** @type {T[]} */ (things), (things.length - 1) | 0);
  return thing;
}

class Classy extends type() {
  /** @type {number} */
  #index;

  /** @param {number} index */
  constructor(index) {
    super();
    this.#index = index;
  }

  /** @returns {number} */
  get index() {
    return this.#index;
  }

  /**
   * @template T
   * @param {readonly T[]} things
   * @returns {T}
   */
  static first(things) {
    const thing = listedGet(/** @type {T[]} */ (things), 0);
    return thing;
  }

  /**
   * @template T
   * @param {readonly T[]} things
   * @returns {T}
   */
  indexed(things) {
    const thing = listedGet(/** @type {T[]} */ (things), this.#index);
    return thing;
  }
}

class Picker extends type() {
  /**
   * @template T
   * @param {readonly T[]} things
   * @returns {T}
   */
  pick(things) {
    return lastOf(things);
  }
}

whatever(1);
/** @type {readonly string[]} */
const someThings = Object.freeze(["a", "b"]);
const first = Classy.first(someThings);
const second = new Classy(1).indexed(someThings);
console.log(`Some things: ${first}, ${second}`);
console.log(`Picked: ${new Picker().pick(someThings)}`);
