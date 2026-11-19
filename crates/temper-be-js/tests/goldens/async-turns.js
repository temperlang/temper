import {
  PromiseBuilder,
  adaptAwaiter,
  bubble,
  isBubble,
  listBuilderToList,
  listedJoin,
  modIntInt,
  runAsync,
  type,
} from "@temperlang/core";

/**
 * @param {number} n
 * @returns {Promise<number>}
 */
export function later(n) {
  /** @type {import("@temperlang/core/async.js").PromiseBuilder<number>} */
  const pb = new PromiseBuilder();
  /**
   * @param {(p: Promise<unknown>) => void} doAwait
   * @returns {Generator<any>}
   */
  function* fn(doAwait) {
    pb.complete(Math.imul(n, 2));
  }
  runAsync(adaptAwaiter(fn));
  return pb.promise;
}

/**
 * @template T
 * @param {() => Generator<T>} make
 * @returns {Generator<T>}
 */
function generated(make) {
  return make();
}

/**
 * @param {number} n
 * @param {(arg0: number) => void} each
 * @returns {Generator<{}>}
 */
export function ticks(n, each) {
  /**
   * @param {(p: Promise<unknown>) => void} doAwait
   * @returns {Generator<any>}
   */
  function* fn(doAwait) {
    {
      let i = 0;
      while (i < n) {
        each(i);
        yield;
        i = (i + 1) | 0;
      }
    }
  }
  return generated(adaptAwaiter(fn));
}

/**
 * @template T
 * @param {number} start
 * @param {(arg0: number) => Generator<T>} make
 * @returns {Generator<T>}
 */
function startedAt(start, make) {
  return make(start);
}

/** @returns {Generator<number>} */
export function fibonacci() {
  /**
   * @param {(p: Promise<unknown>) => void} doAwait
   * @returns {Generator<any>}
   */
  function* fn(doAwait) {
    let a = 0;
    let b = 1;
    while (true) {
      yield a;
      const next = (a + b) | 0;
      a = b;
      b = next;
    }
  }
  return generated(adaptAwaiter(fn));
}

/**
 * @param {number} start
 * @returns {Generator<number>}
 */
export function collatz(start) {
  /**
   * @param {(p: Promise<unknown>) => void} doAwait
   * @param {number} first
   * @returns {Generator<any>}
   */
  function* fn(doAwait, first) {
    let n = first;
    while (true) {
      yield n;
      if (n === 1) {
        break;
      }
      if (modIntInt(n, 2) === 0) {
        n = (n / 2) | 0;
      } else {
        n = (Math.imul(3, n) + 1) | 0;
      }
    }
  }
  return startedAt(start, adaptAwaiter(fn));
}

/**
 * @param {Generator<number>} g
 * @param {number} n
 * @returns {readonly number[]}
 */
function take(g, n) {
  /** @type {number[]} */
  const taken = [];
  while (taken.length < n) {
    /** @type {IteratorResult<number, void>} */
    const r = g.next();
    if (!r.done) {
      taken.push(/** @type {IteratorYieldResult<number>} */ (/** @type {unknown} */ (r)).value);
    } else {
      break;
    }
  }
  return listBuilderToList(taken);
}

/**
 * @param {readonly number[]} ints
 * @returns {string}
 */
function shown(ints) {
  return listedJoin(/** @type {number[]} */ (ints), " ", (i) => i.toString());
}

/**
 * @param {number} n
 * @returns {number}
 */
export function halve(n) {
  if (modIntInt(n, 2) !== 0) {
    bubble();
  }
  return (n / 2) | 0;
}

class Counter extends type() {
  /** @type {number} */
  #n;

  constructor() {
    super();
    this.#n = 0;
  }

  /** @returns {number} */
  get n() {
    return this.#n;
  }

  /** @param {number} value */
  set n(value) {
    this.#n = value;
  }

  /**
   * @param {string} tag
   * @param {number} times
   */
  bump(tag, times) {
    console.log(`${tag} start`);
    {
      let i = 0;
      while (i < times) {
        const before = this.#n;
        this.#n = (before + 1) | 0;
        i = (i + 1) | 0;
      }
    }
    console.log(`${tag} done n=${this.#n}`);
  }
}

/** @param {number} i */
function fn(i) {
  console.log(`tick ${i}`);
}

/** @type {Generator<{}>} */
const t = ticks(2, fn);
t.next();
t.next();
t.next();
console.log(`fibonacci ${shown(take(fibonacci(), 10))}`);
console.log(`collatz ${shown(take(collatz(6), 100))}`);
/** @type {Generator<number>} */
const fib = fibonacci();
take(fib, 5);
console.log(`then ${shown(take(fib, 3))}`);
const c = new Counter();
/** @type {import("@temperlang/core/async.js").PromiseBuilder<number>} */
const gateA = new PromiseBuilder();
/** @type {import("@temperlang/core/async.js").PromiseBuilder<number>} */
const gateB = new PromiseBuilder();

/**
 * @param {(p: Promise<unknown>) => void} doAwait
 * @returns {Generator<any>}
 */
function* fn2(doAwait) {
  c.bump("A", 10000);
  /** @type {number} */
  let k;
  try {
    k = yield doAwait(gateA.promise);
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    k = -1;
  }
  c.bump(`A${k}`, 10000);
}

runAsync(adaptAwaiter(fn2));

/**
 * @param {(p: Promise<unknown>) => void} doAwait
 * @returns {Generator<any>}
 */
function* fn3(doAwait) {
  c.bump("B", 10000);
  /** @type {number} */
  let k;
  try {
    k = yield doAwait(gateB.promise);
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    k = -1;
  }
  c.bump(`B${k}`, 10000);
}

runAsync(adaptAwaiter(fn3));

/**
 * @param {(p: Promise<unknown>) => void} doAwait
 * @returns {Generator<any>}
 */
function* fn4(doAwait) {
  console.log("opener: calling complete");
  gateA.complete(1);
  gateB.complete(2);
  console.log("opener: complete returned");
}

runAsync(adaptAwaiter(fn4));
