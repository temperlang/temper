import {
  PromiseBuilder,
  adaptAwaiter,
  isBubble,
  panic,
  runAsync,
} from "@temperlang/core";

/**
 * @param {(p: Promise<unknown>) => void} doAwait
 * @returns {Generator<any>}
 */
function* fn(doAwait) {
  try {
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<number>} */
    const b = new PromiseBuilder();
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<number>} */
    const firstDone = new PromiseBuilder();
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<number>} */
    const secondDone = new PromiseBuilder();
    /**
     * @param {(p: Promise<unknown>) => void} doAwait_1
     * @returns {Generator<any>}
     */
    function* fn2(doAwait_1) {
      /** @type {number} */
      let v;
      try {
        v = yield doAwait_1(b.promise);
      } catch (e$) {
        if (!isBubble(e$)) throw e$;
        v = -1;
      }
      firstDone.complete(v);
    }
    runAsync(adaptAwaiter(fn2));
    /**
     * @param {(p: Promise<unknown>) => void} doAwait_1
     * @returns {Generator<any>}
     */
    function* fn3(doAwait_1) {
      /** @type {number} */
      let v;
      try {
        v = yield doAwait_1(b.promise);
      } catch (e$) {
        if (!isBubble(e$)) throw e$;
        v = -1;
      }
      secondDone.complete(v);
    }
    runAsync(adaptAwaiter(fn3));
    /**
     * @param {(p: Promise<unknown>) => void} doAwait_1
     * @returns {Generator<any>}
     */
    function* fn4(doAwait_1) {
      b.complete(7);
    }
    runAsync(adaptAwaiter(fn4));
    const first = yield doAwait(firstDone.promise);
    const second = yield doAwait(secondDone.promise);
    console.log(`after complete: first got ${first}, second got ${second}`);
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    panic("panic", e$);
  }
  try {
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<number>} */
    const b2 = new PromiseBuilder();
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<number>} */
    const firstDone2 = new PromiseBuilder();
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<number>} */
    const secondDone2 = new PromiseBuilder();
    /**
     * @param {(p: Promise<unknown>) => void} doAwait_1
     * @returns {Generator<any>}
     */
    function* fn5(doAwait_1) {
      /** @type {number} */
      let v;
      try {
        v = yield doAwait_1(b2.promise);
      } catch (e$) {
        if (!isBubble(e$)) throw e$;
        v = -1;
      }
      firstDone2.complete(v);
    }
    runAsync(adaptAwaiter(fn5));
    /**
     * @param {(p: Promise<unknown>) => void} doAwait_1
     * @returns {Generator<any>}
     */
    function* fn6(doAwait_1) {
      /** @type {number} */
      let v;
      try {
        v = yield doAwait_1(b2.promise);
      } catch (e$) {
        if (!isBubble(e$)) throw e$;
        v = -1;
      }
      secondDone2.complete(v);
    }
    runAsync(adaptAwaiter(fn6));
    /**
     * @param {(p: Promise<unknown>) => void} doAwait_1
     * @returns {Generator<any>}
     */
    function* fn7(doAwait_1) {
      b2.breakPromise();
    }
    runAsync(adaptAwaiter(fn7));
    const first2 = yield doAwait(firstDone2.promise);
    const second2 = yield doAwait(secondDone2.promise);
    console.log(`after breakPromise: first got ${first2}, second got ${second2}`);
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    panic("panic", e$);
  }
}

runAsync(adaptAwaiter(fn));
