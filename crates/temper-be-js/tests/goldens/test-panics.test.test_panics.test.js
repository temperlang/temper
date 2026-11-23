import { bubble, endTest, isBubble, listedGet, panic } from "@temperlang/core";
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

it("passes before", async function () {
  const test = new Test();
  let failure;
  try {
    {
      const actual = (1 + 1) | 0;
      const expected = 2;
      test.assert(actual === expected, () => `expected 1 + 1 == (${expected}) not (${actual})`);
    }
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("panics reading past the end", async function () {
  const test = new Test();
  let failure;
  try {
    /** @type {number} */
    let n;
    try {
      n = at(Object.freeze([1, 2]), 5);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      n = -1;
    }
    {
      const actual = n;
      const expected = -1;
      test.assert(actual === expected, () => `expected n == (${expected}) not (${actual})`);
    }
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("dies outright", async function () {
  const test = new Test();
  let failure;
  try {
    panic();
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("fails an assertion", async function () {
  const test = new Test();
  let failure;
  try {
    test.assert(((1 + 1) | 0) === 3, () => "wrong on purpose");
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

it("dies after failing", async function () {
  const test = new Test();
  let failure;
  try {
    test.assert(((1 + 1) | 0) === 3, () => "failed before");
    panic();
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});
