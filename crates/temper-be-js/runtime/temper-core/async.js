import { BrokenPromise } from "./core.js";


// Launches waiting for their first step, and I/O in flight
// (`trackAsyncIo`), which `finishAsyncWork` waits for.
let busy = 0;
// How many `finishAsyncWork` calls are waiting: while any is, a failure
// that escapes a step is kept for them, else thrown as before.
let finishing = 0;
/** @type {{ failure: unknown } | null} */
let escaped = null;

/**
 * Runs one step of an async block.
 * @param {() => unknown} run
 */
const step = (run) => {
  try {
    run();
  } catch (e) {
    if (finishing === 0) throw e;
    if (escaped === null) escaped = { failure: e };
  }
};

/**
 * @param generatorFactory {() => Generator<unknown>}
 */
export const runAsync = (generatorFactory) => {
  let generator = generatorFactory();
  busy += 1;
  setTimeout(() => {
    busy -= 1;
    step(() => generator.next());
  });
}

/**
 * Counts `promise` as I/O in flight, as std/net's requests, until it
 * settles, so `finishAsyncWork` waits for it.
 * @template T
 * @param {Promise<T>} promise
 * @returns {Promise<T>}
 */
export const trackAsyncIo = (promise) => {
  busy += 1;
  const settled = () => { busy -= 1; };
  promise.then(settled, settled);
  return promise;
}

/**
 * Resolves once no async block is waiting for its first step and no I/O
 * is in flight, as generated tests await after each test's body, or
 * rejects with the first failure that escaped a step meanwhile, so the
 * test fails with it. Blocks still awaiting a promise that nothing
 * settles stay waiting.
 * @returns {Promise<void>}
 */
export const finishAsyncWork = async () => {
  finishing += 1;
  try {
    // Each round lets what's queued so far, and what it queues as
    // microtasks, run first: `setImmediate`, where there is one, since
    // Node clamps `setTimeout` to a millisecond, which every test would
    // pay; a launch's timer holds `busy` until it runs, so rounds go on
    // until it has.
    do {
      await new Promise((resolve) => nextRound(resolve));
    } while (busy > 0);
  } finally {
    finishing -= 1;
  }
  const kept = escaped;
  escaped = null;
  if (kept !== null) throw kept.failure;
}

/** @type {(f: () => void) => void} */
const nextRound = typeof setImmediate === "function"
  ? (f) => { setImmediate(f); }
  : (f) => { setTimeout(f); };

/**
 * Ends a test, as generated tests do after its body, even one that threw
 * (`failure`, else `undefined`): finishes the async work it started
 * (`finishAsyncWork`), then fails it if it has failures so far
 * (`softFailToHard`), each even if the other failed. The body's own
 * failure wins, and the async work's wins over the soft failures; what
 * fails besides is added to the failure that wins, as its `suppressed`,
 * which test reports show, or else written to stderr.
 * @param {{ softFailToHard: () => void }} test
 * @param {unknown} failure
 * @returns {Promise<void>}
 */
export const endTest = async (test, failure) => {
  /** @type {unknown[]} */
  const failures = failure === undefined ? [] : [failure];
  try {
    await finishAsyncWork();
  } catch (e) {
    failures.push(e);
  }
  try {
    test.softFailToHard();
  } catch (e) {
    failures.push(e);
  }
  const [first, ...rest] = failures;
  if (rest.length !== 0) {
    try {
      /** @type {{ suppressed?: unknown }} */ (first).suppressed = rest.length === 1 ? rest[0] : rest;
    } catch {
      console.error("The test also failed:", ...rest);
    }
  }
  if (failure === undefined && first !== undefined) throw first;
}

/**
 * Adapts a generator that takes an extra doAwait function.
 * This allows adapting `await p` to `yield doAwait(p)`.
 *
 * @template T
 * @template {Array} A The argument list
 * @param generatorFactory {(doAsync: ((p: Promise<unknown>) => void), ...rest: A) => Generator<T>}
 * @returns {(...A) => Generator<T>}
 */
export const adaptAwaiter = (generatorFactory) => {
  return (...args) => {
    /**
     * @template R
     * @param {Promise<R>} p
     */
    const doAwait = (p) => {
      // Each await of a broken promise throws a new BrokenPromise,
      // whatever it was rejected with (host code may give any reason).
      p.then(
        (result) => { step(() => generator.next(result)) },
        () => { step(() => generator.throw(new BrokenPromise())) },
      );
    };

    /** @type {Generator<T>} */
    const generator = generatorFactory(doAwait, ...args);

    return generator;
  }
}

/**
 * @template R
 */
export class PromiseBuilder {
  /** @type {Promise<R>} */
  promise;
  /** @type {(value: (PromiseLike<R> | R)) => void} */
  resolve;
  /** @type {(reason?: any) => void} */
  reject;

  constructor() {
    this.promise = new Promise(
      (resolve, reject) => {
        this.resolve = resolve;
        this.reject = reject;
      }
    );
    // A Temper program may break a promise before any block awaits it,
    // for example from top-level code while the awaiting block waits for
    // its `setTimeout` in `runAsync`. Node treats a rejection that has no
    // handler when the microtask queue drains as fatal. This handler marks
    // the promise as handled; an `await` attached later still sees the
    // rejection through its own `then`.
    this.promise.catch(() => {});
  }

  /** @param {R} value */
  complete(value) {
    this.resolve(value)
  }

  /** @param {any} [reason] */
  breakPromise(reason = new BrokenPromise()) {
    this.reject(reason);
  }
}

// We might customize this in the future, but actual global console works today.
export const globalConsole = console;
