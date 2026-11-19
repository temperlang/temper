import { bubble, isBubble, listedGet, panic } from "@temperlang/core";
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

it("passes before", function () {
  const test = new Test();
  try {
    {
      const actual = (1 + 1) | 0;
      const expected = 2;
      test.assert(actual === expected, () => `expected 1 + 1 == (${expected}) not (${actual})`);
    }
  } finally {
    test.softFailToHard();
  }
});

it("panics reading past the end", function () {
  const test = new Test();
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
  } finally {
    test.softFailToHard();
  }
});

it("panics outright", function () {
  const test = new Test();
  try {
    panic();
  } finally {
    test.softFailToHard();
  }
});

it("fails an assertion", function () {
  const test = new Test();
  try {
    test.assert(((1 + 1) | 0) === 3, () => "wrong on purpose");
  } finally {
    test.softFailToHard();
  }
});

it("passes after", function () {
  const test = new Test();
  try {
    {
      const actual = at(Object.freeze([1, 2]), 1);
      const expected = 2;
      test.assert(actual === expected, () => `expected at([1, 2], 1) == (${expected}) not (${actual})`);
    }
  } finally {
    test.softFailToHard();
  }
});
