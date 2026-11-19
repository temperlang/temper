import { type } from "@temperlang/core";
import { it } from "node:test";
import {
  base,
  checked,
  shown,
  shownCount,
  shownCount$set,
} from "../reach.internal.js";
import { Test } from "@temperlang/std/testing";

/**
 * @param {number} n
 * @returns {number}
 */
function forTests(n) {
  return Math.imul(n, testFactor);
}

/** @param {TestShape} shape */
function addArea(shape) {
  testCount = (testCount + shape.area()) | 0;
}

class TestShape extends type() {
  /** @type {() => number} */
  area = /** @type {any} */ (undefined);
}

class TestSquare extends type(TestShape) {
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
}

const testFactor = Math.imul(base, 2);
let testCount = 0;

it("test parts", function () {
  const test = new Test();
  try {
    const shape = new TestSquare(forTests(2));
    addArea(shape);
    testCount = (testCount + shape.area()) | 0;
    {
      const actual = testCount;
      const expected = 32;
      test.assert(actual === expected, () => `expected testCount == (${expected}) not (${actual})`);
    }
    {
      const actual = checked;
      const expected = 3;
      test.assert(actual === expected, () => `expected checked == (${expected}) not (${actual})`);
    }
    {
      const actual = shownCount;
      const expected = 2;
      test.assert(actual === expected, () => `expected shownCount == (${expected}) not (${actual})`);
    }
    shownCount$set(0);
    shown(0);
    {
      const actual = shownCount;
      const expected = 1;
      test.assert(actual === expected, () => `expected shownCount == (${expected}) not (${actual})`);
    }
  } finally {
    test.softFailToHard();
  }
});
