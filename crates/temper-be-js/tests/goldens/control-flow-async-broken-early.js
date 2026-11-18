import {
  PromiseBuilder,
  adaptAwaiter,
  isBubble,
  runAsync,
} from "@temperlang/core";

/** @type {import("@temperlang/core/async.js").PromiseBuilder<number>} */
const b = new PromiseBuilder();

/**
 * @param {(p: Promise<unknown>) => void} doAwait
 * @returns {Generator<any>}
 */
function* fn(doAwait) {
  /** @type {number} */
  let v;
  try {
    v = yield doAwait(b.promise);
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    v = -1;
  }
  console.log(`got ${v}`);
}

runAsync(adaptAwaiter(fn));
b.breakPromise();
