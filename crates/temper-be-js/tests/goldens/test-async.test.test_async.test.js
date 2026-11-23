import {
  PromiseBuilder,
  adaptAwaiter,
  finishAsyncWork,
  isBubble,
  panic,
  runAsync,
} from "@temperlang/core";
import { it } from "node:test";
import { Test } from "@temperlang/std/testing";

it("fails in an async block", async function () {
  const test = new Test();
  try {
    /**
     * @param {(p: Promise<unknown>) => void} doAwait
     * @returns {Generator<any>}
     */
    function* fn(doAwait) {
      test.assert(((1 + 1) | 0) === 3, () => "failed in async");
    }
    runAsync(adaptAwaiter(fn));
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("passes in an async block", async function () {
  const test = new Test();
  try {
    /**
     * @param {(p: Promise<unknown>) => void} doAwait
     * @returns {Generator<any>}
     */
    function* fn(doAwait) {
      test.assert(((1 + 1) | 0) === 2, () => "not shown");
    }
    runAsync(adaptAwaiter(fn));
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("fails in a later turn", async function () {
  const test = new Test();
  try {
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<number>} */
    const pb = new PromiseBuilder();
    /**
     * @param {(p: Promise<unknown>) => void} doAwait
     * @returns {Generator<any>}
     */
    function* fn(doAwait) {
      /** @type {number} */
      let n;
      try {
        n = yield doAwait(pb.promise);
      } catch (e$) {
        if (!isBubble(e$)) throw e$;
        n = -1;
      }
      test.assert(n === 2, () => `later turn got ${n}`);
    }
    runAsync(adaptAwaiter(fn));
    /**
     * @param {(p: Promise<unknown>) => void} doAwait
     * @returns {Generator<any>}
     */
    function* fn3(doAwait) {
      pb.complete(1);
    }
    runAsync(adaptAwaiter(fn3));
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("passes after a later turn", async function () {
  const test = new Test();
  try {
    {
      const actual = (1 + 1) | 0;
      const expected = 2;
      test.assert(actual === expected, () => `expected 1 + 1 == (${expected}) not (${actual})`);
    }
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("fails hard in an async block", async function () {
  const test = new Test();
  try {
    /**
     * @param {(p: Promise<unknown>) => void} doAwait
     * @returns {Generator<any>}
     */
    function* fn(doAwait) {
      test.fail("failed hard in async");
    }
    runAsync(adaptAwaiter(fn));
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("dies in an async block", async function () {
  const test = new Test();
  try {
    /**
     * @param {(p: Promise<unknown>) => void} doAwait
     * @returns {Generator<any>}
     */
    function* fn(doAwait) {
      panic();
    }
    runAsync(adaptAwaiter(fn));
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("passes after", async function () {
  const test = new Test();
  try {
    {
      const actual = (1 + 1) | 0;
      const expected = 2;
      test.assert(actual === expected, () => `expected 1 + 1 == (${expected}) not (${actual})`);
    }
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});

it("waits on nothing", async function () {
  const test = new Test();
  try {
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<number>} */
    const never = new PromiseBuilder();
    /**
     * @param {(p: Promise<unknown>) => void} doAwait
     * @returns {Generator<any>}
     */
    function* fn(doAwait) {
      /** @type {number} */
      let n;
      try {
        n = yield doAwait(never.promise);
      } catch (e$) {
        if (!isBubble(e$)) throw e$;
        n = -1;
      }
      test.assert(false, () => `ran ${n}`);
    }
    runAsync(adaptAwaiter(fn));
  } finally {
    await finishAsyncWork();
    test.softFailToHard();
  }
});
