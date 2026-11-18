import {
  PromiseBuilder,
  adaptAwaiter,
  isBubble,
  runAsync,
} from "@temperlang/core";

/** @type {import("@temperlang/core/async.js").PromiseBuilder<string>} */
const once = new PromiseBuilder();
once.complete("same");
/** @type {import("@temperlang/core/async.js").PromiseBuilder<string>} */
const later = new PromiseBuilder();

/**
 * @param {(p: Promise<unknown>) => void} doAwait
 * @returns {Generator<any>}
 */
function* fn(doAwait) {
  {
    let i = 0;
    while (i < 2) {
      /** @type {string} */
      let s;
      try {
        s = yield doAwait(once.promise);
      } catch (e$) {
        if (!isBubble(e$)) throw e$;
        s = "?";
      }
      console.log(`${s} ${i}`);
      i = (i + 1) | 0;
    }
  }
  let j = 0;
  while (j < 2) {
    /** @type {string} */
    let s;
    try {
      s = yield doAwait(later.promise);
    } catch (e$) {
      if (!isBubble(e$)) throw e$;
      s = "?";
    }
    console.log(`${s} ${j}`);
    j = (j + 1) | 0;
  }
  /** @type {string} */
  let t;
  try {
    t = yield doAwait(once.promise);
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    t = "?";
  }
  console.log(`${t} after the loops`);
}

runAsync(adaptAwaiter(fn));
later.complete("later");
