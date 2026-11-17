import { float64ToString, listedGet, type } from "@temperlang/core";
import { Test } from "@temperlang/std/testing";

/**
 * @param {Vec2} vec
 * @returns {number}
 */
export function normSquared(vec) {
  return vec.x * vec.x + vec.y * vec.y;
}

/**
 * @param {Test} t
 * @param {TestHelper} helper
 * @param {readonly (readonly string[])[]} strings
 */
export function assertMoreThings(t, helper, strings) {
  {
    let i = 0;
    while (i < strings.length) {
      {
        let j = 0;
        while (j < listedGet(/** @type {(readonly string[])[]} */ (strings), i).length) {
          {
            const actual = helper.i;
            const expected = helper.j;
            t.assert(actual === expected, () => `expected helper.i == (${expected}) not (${actual})`);
          }
          const s = listedGet(/** @type {string[]} */ (listedGet(/** @type {(readonly string[])[]} */ (strings), i)), j);
          {
            const actual = s;
            const expected = "hi";
            t.assert(actual === expected, () => `expected s == (${expected}) not (${actual})`);
          }
          j = (j + 1) | 0;
        }
      }
      i = (i + 1) | 0;
    }
  }
}

/**
 * @param {TestHelper} helper
 * @returns {number}
 */
function sumOf(helper) {
  return (helper.i + helper.j) | 0;
}

/**
 * @param {TestHelper} helper
 * @returns {number}
 */
function keptForTests(helper) {
  return sumOf(helper);
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

export class TestHelper extends type() {
  /** @type {number} */
  #i;
  /** @type {number} */
  #j;

  /**
   * @param {{ i: number, j: number }} props
   * @returns {TestHelper}
   */
  static new(props) {
    return new TestHelper(props.i, props.j);
  }

  /**
   * @param {number} i
   * @param {number} j
   */
  constructor(i, j) {
    super();
    this.#i = i;
    this.#j = j;
  }

  /** @returns {number} */
  get i() {
    return this.#i;
  }

  /** @returns {number} */
  get j() {
    return this.#j;
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

const name = "case";
/** @type {readonly Vec2[]} */
export const unitVecs = Object.freeze([new Vec2(1.0, 0.0), new Vec2(0.0, 1.0)]);
