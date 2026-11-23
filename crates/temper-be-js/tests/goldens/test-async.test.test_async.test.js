import {
  PromiseBuilder,
  adaptAwaiter,
  endTest,
  isBubble,
  listedGet,
  panic,
  runAsync,
} from "@temperlang/core";
import { it } from "node:test";
import { Test } from "@temperlang/std/testing";

it("fails in an async block", async function () {
  const test = new Test();
  let failure;
  try {
    /**
     * @param {(p: Promise<unknown>) => void} doAwait
     * @returns {Generator<any>}
     */
    function* fn(doAwait) {
      test.assert(((1 + 1) | 0) === 3, () => "failed in async");
    }
    runAsync(adaptAwaiter(fn));
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("passes in an async block", async function () {
  const test = new Test();
  let failure;
  try {
    /**
     * @param {(p: Promise<unknown>) => void} doAwait
     * @returns {Generator<any>}
     */
    function* fn(doAwait) {
      test.assert(((1 + 1) | 0) === 2, () => "not shown");
    }
    runAsync(adaptAwaiter(fn));
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("fails in a later turn", async function () {
  const test = new Test();
  let failure;
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
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("passes after a later turn", async function () {
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

it("fails hard in an async block", async function () {
  const test = new Test();
  let failure;
  try {
    /**
     * @param {(p: Promise<unknown>) => void} doAwait
     * @returns {Generator<any>}
     */
    function* fn(doAwait) {
      test.fail("failed hard in async");
    }
    runAsync(adaptAwaiter(fn));
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("dies in an async block", async function () {
  const test = new Test();
  let failure;
  try {
    /**
     * @param {(p: Promise<unknown>) => void} doAwait
     * @returns {Generator<any>}
     */
    function* fn(doAwait) {
      panic();
    }
    runAsync(adaptAwaiter(fn));
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

it("waits on nothing", async function () {
  const test = new Test();
  let failure;
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
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});

it("own failure wins", async function () {
  const test = new Test();
  let failure;
  try {
    test.assert(false, () => "soft failure");
    /**
     * @param {(p: Promise<unknown>) => void} doAwait
     * @returns {Generator<any>}
     */
    function* fn2(doAwait) {
      test.fail("async failure");
    }
    runAsync(adaptAwaiter(fn2));
    /** @type {readonly number[]} */
    const ls = Object.freeze([1]);
    {
      const actual = listedGet(/** @type {number[]} */ (ls), 5);
      const expected = 1;
      test.assert(actual === expected, () => `expected ls[5] == (${expected}) not (${actual})`);
    }
  } catch (e) {
    failure = e;
    throw e;
  } finally {
    await endTest(test, failure);
  }
});
