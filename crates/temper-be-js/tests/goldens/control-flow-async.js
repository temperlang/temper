import {
  PromiseBuilder,
  adaptAwaiter,
  empty,
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
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<string>} */
    const b = new PromiseBuilder();
    b.complete("Completed synchronously");
    console.log(yield doAwait(b.promise));
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    panic("panic", e$);
  }
  {
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<string>} */
    const b2 = new PromiseBuilder();
    b2.breakPromise();
    /** @type {string} */
    let t$0;
    try {
      t$0 = yield doAwait(b2.promise);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      t$0 = "Broken synchronously";
    }
    console.log(t$0);
  }
  try {
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<string>} */
    const b3 = new PromiseBuilder();
    /**
     * @param {(p: Promise<unknown>) => void} doAwait_1
     * @returns {Generator<any>}
     */
    function* fn2(doAwait_1) {
      b3.complete("Completed asynchronously");
    }
    runAsync(adaptAwaiter(fn2));
    console.log(yield doAwait(b3.promise));
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    panic("panic", e$);
  }
  try {
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<string>} */
    const b4 = new PromiseBuilder();
    b4.complete("Worth doing twice");
    console.log(yield doAwait(b4.promise));
    console.log(yield doAwait(b4.promise));
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    panic("panic", e$);
  }
  try {
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<{}>} */
    const a = new PromiseBuilder();
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<{}>} */
    const b5 = new PromiseBuilder();
    /**
     * @param {(p: Promise<unknown>) => void} doAwait_1
     * @returns {Generator<any>}
     */
    function* fn3(doAwait_1) {
      try {
        yield doAwait_1(b5.promise);
      } catch (e$) {
        if (!isBubble(e$)) throw e$;
        panic("panic", e$);
      }
      console.log("After");
      a.complete(empty());
    }
    runAsync(adaptAwaiter(fn3));
    /**
     * @param {(p: Promise<unknown>) => void} doAwait_1
     * @returns {Generator<any>}
     */
    function* fn4(doAwait_1) {
      console.log("Before");
      b5.complete(empty());
    }
    runAsync(adaptAwaiter(fn4));
    yield doAwait(a.promise);
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    panic("panic", e$);
  }
  try {
    console.log("What is your favourite colour?");
    /** @type {import("@temperlang/core/async.js").PromiseBuilder<string>} */
    const b6 = new PromiseBuilder();
    b6.complete("Blue");
    b6.complete("No, yell...");
    console.log(yield doAwait(b6.promise));
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
  }
}

runAsync(adaptAwaiter(fn));
