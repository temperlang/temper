import {
  bubble,
  finishAsyncWork,
  isBubble,
  listedGet,
  panic,
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

it("passes before", async function () {
  const test = new Test();
  try {
    {
      const actual = at(Object.freeze([1, 2]), 1);
      const expected = 2;
      test.assert(actual === expected, () => `expected at([1, 2], 1) == (${expected}) not (${actual})`);
    }
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("passes when the block panics", async function () {
  const test = new Test();
  try {
    function fn() {
      at(Object.freeze([1, 2]), 5);
    }
    test.assertPanics(fn);
    function fn2() {
      panic();
    }
    test.assertPanics(fn2);
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("passes past an orelse", async function () {
  const test = new Test();
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
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("fails when the block returns", async function () {
  const test = new Test();
  try {
    function fn() {
      at(Object.freeze([1, 2]), 0);
    }
    test.assertPanics(fn);
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("fails with its message", async function () {
  const test = new Test();
  try {
    function fn() {
      at(Object.freeze([1, 2]), 1);
    }
    test.assertPanics(fn, "reading index 1 panics");
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("fails when the block bubbles", async function () {
  const test = new Test();
  try {
    function fn() {
      at(Object.freeze([1, 2]), 99);
    }
    test.assertPanics(fn);
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("fails with its message when the block bubbles", async function () {
  const test = new Test();
  try {
    function fn() {
      at(Object.freeze([1, 2]), 99);
    }
    test.assertPanics(fn, "reading index 99 panics");
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("goes on after failing", async function () {
  const test = new Test();
  try {
    function fn() {
      at(Object.freeze([1, 2]), 0);
    }
    test.assertPanics(fn);
    test.assert(false, () => "went on");
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("nests", async function () {
  const test = new Test();
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
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("keeps what the block changed", async function () {
  const test = new Test();
  try {
    let n = 0;
    function fn() {
      n = (n + 1) | 0;
      panic();
    }
    test.assertPanics(fn);
    test.assert(n === 1, () => "lost the change");
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("fails on a panic after the block", async function () {
  const test = new Test();
  try {
    function fn() {
      panic();
    }
    test.assertPanics(fn);
    at(Object.freeze([1, 2]), 7);
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("ends on a fail in the block", async function () {
  const test = new Test();
  try {
    function fn() {
      test.fail("failed inside");
    }
    test.assertPanics(fn);
    test.fail("swallowed");
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("passes after", async function () {
  const test = new Test();
  try {
    {
      const actual = at(Object.freeze([1, 2]), 0);
      const expected = 1;
      test.assert(actual === expected, () => `expected at([1, 2], 0) == (${expected}) not (${actual})`);
    }
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});
