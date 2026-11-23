import {
  endTest,
  eqFloat64,
  float64ToString,
  listedGet,
  type,
} from "@temperlang/core";
import { it } from "node:test";
import { Vec2, normSquared, unitVecs } from "../asserts.internal.js";
import { Test } from "@temperlang/std/testing";

/**
 * @param {Test} t
 * @param {TestHelper} helper
 * @param {readonly (readonly string[])[]} strings
 */
function assertMoreThings(t, helper, strings) {
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

class TestHelper extends type() {
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

it("a test case", async function () {
  const test = new Test();
  let failure;
  try {
    test.assert(eqFloat64(normSquared(new Vec2(0.0, 0.0)), 0.0), () => "Oh noes");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("a failing test", async function () {
  const test = new Test();
  let failure;
  try {
    test.assert(false, () => "because it's wrong");
    const helper = new TestHelper(3, 2);
    assertMoreThings(test, helper, Object.freeze([Object.freeze(["bye"])]));
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("exported", async function () {
  const test = new Test();
  let failure;
  try {
    const vec = new Vec2(1.5, 2.5);
    {
      const actual = vec.toString();
      const expected = "(1.5, 2.5) size squared 8.5";
      test.assert(actual === expected, () => `expected vec.toString() == (${expected}) not (${actual})`);
    }
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("module values", async function () {
  const test = new Test();
  let failure;
  try {
    {
      const length = unitVecs.length;
      let i = 0;
      while (i < length) {
        const vec = listedGet(/** @type {Vec2[]} */ (unitVecs), i);
        i = (i + 1) | 0;
        {
          const actual = normSquared(vec);
          const expected = 1.0;
          test.assert(eqFloat64(actual, expected), () => `expected normSquared(vec) == (${float64ToString(expected)}) not (${float64ToString(actual)})`);
        }
      }
    }
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});
