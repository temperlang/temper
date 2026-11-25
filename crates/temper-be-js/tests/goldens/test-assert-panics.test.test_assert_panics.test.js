import {
  bubble,
  divIntInt,
  endTest,
  isBubble,
  listedGet,
  modInt64,
  panic,
  requireInstanceOf,
  type,
} from "@temperlang/core";
import { it } from "node:test";
import { Test } from "@temperlang/std/testing";

/**
 * @param {readonly number[]} ls
 * @param {number} i
 * @returns {number}
 */
function at(ls, i) {
  if (i === 99) {
    bubble();
  }
  return listedGet(/** @type {number[]} */ (ls), i);
}

/**
 * @param {number} a
 * @param {number} b
 * @returns {number}
 */
function divide(a, b) {
  return divIntInt(a, b);
}

/**
 * @param {bigint} a
 * @param {bigint} b
 * @returns {bigint}
 */
function remainder(a, b) {
  return modInt64(a, b);
}

/**
 * @param {Shape} s
 * @returns {Square}
 */
function squared(s) {
  return requireInstanceOf(s, Square);
}

class Shape extends type() {}

class Square extends type(Shape) {}

class Circle extends type(Shape) {}

it("passes before", async function () {
  const test = new Test();
  let failure;
  try {
    {
      const actual = at(Object.freeze([1, 2]), 1);
      const expected = 2;
      test.assert(actual === expected, () => `expected at([1, 2], 1) == (${expected}) not (${actual})`);
    }
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("passes when the block panics", async function () {
  const test = new Test();
  let failure;
  try {
    function fn() {
      at(Object.freeze([1, 2]), 5);
    }
    test.assertPanics(fn);
    function fn2() {
      panic();
    }
    test.assertPanics(fn2);
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("passes when division by zero or a cast panics", async function () {
  const test = new Test();
  let failure;
  try {
    function fn() {
      divide(1, 0);
    }
    test.assertPanics(fn);
    function fn2() {
      remainder(1n, 0n);
    }
    test.assertPanics(fn2);
    function fn3() {
      squared(new Circle());
    }
    test.assertPanics(fn3);
    squared(new Square());
    {
      const actual = divide(-7, 2);
      const expected = -3;
      test.assert(actual === expected, () => `expected divide(-7, 2) == (${expected}) not (${actual})`);
    }
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("passes past an orelse", async function () {
  const test = new Test();
  let failure;
  try {
    function fn() {
      /** @type {number} */
      let n;
      try {
        n = at(Object.freeze([1, 2]), 5);
      } catch (e$) {
        if (!isBubble(e$)) throw e$;
        n = -1;
      }
      test.assert(n === -1, () => "caught by an orelse");
    }
    test.assertPanics(fn);
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("fails when the block returns", async function () {
  const test = new Test();
  let failure;
  try {
    function fn() {
      at(Object.freeze([1, 2]), 0);
    }
    test.assertPanics(fn);
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("fails with its message", async function () {
  const test = new Test();
  let failure;
  try {
    function fn() {
      at(Object.freeze([1, 2]), 1);
    }
    test.assertPanics(fn, "reading index 1 panics");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("fails when the block bubbles", async function () {
  const test = new Test();
  let failure;
  try {
    function fn() {
      at(Object.freeze([1, 2]), 99);
    }
    test.assertPanics(fn);
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("fails with its message when the block bubbles", async function () {
  const test = new Test();
  let failure;
  try {
    function fn() {
      at(Object.freeze([1, 2]), 99);
    }
    test.assertPanics(fn, "reading index 99 panics");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("goes on after failing", async function () {
  const test = new Test();
  let failure;
  try {
    function fn() {
      at(Object.freeze([1, 2]), 0);
    }
    test.assertPanics(fn);
    test.assert(false, () => "went on");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("nests", async function () {
  const test = new Test();
  let failure;
  try {
    function fn() {
      function fn2() {
        panic();
      }
      test.assertPanics(fn2);
      function fn3() {
        at(Object.freeze([1, 2]), 0);
      }
      test.assertPanics(fn3, "inner returns");
      panic();
    }
    test.assertPanics(fn);
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("keeps what the block changed", async function () {
  const test = new Test();
  let failure;
  try {
    let n = 0;
    function fn() {
      n = (n + 1) | 0;
      panic();
    }
    test.assertPanics(fn);
    test.assert(n === 1, () => "lost the change");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("fails on a panic after the block", async function () {
  const test = new Test();
  let failure;
  try {
    function fn() {
      panic();
    }
    test.assertPanics(fn);
    at(Object.freeze([1, 2]), 7);
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("ends on a fail in the block", async function () {
  const test = new Test();
  let failure;
  try {
    function fn() {
      test.fail("failed inside");
    }
    test.assertPanics(fn);
    test.fail("swallowed");
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("passes after", async function () {
  const test = new Test();
  let failure;
  try {
    {
      const actual = at(Object.freeze([1, 2]), 0);
      const expected = 1;
      test.assert(actual === expected, () => `expected at([1, 2], 0) == (${expected}) not (${actual})`);
    }
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});
