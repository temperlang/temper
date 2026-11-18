import { Bubble, isBubble } from "./core.js";


/**
 * @param generatorFactory {() => Generator<unknown>}
 */
export const runAsync = (generatorFactory) => {
  let generator = generatorFactory();
  setTimeout(() => { generator.next() });
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
      // A broken promise bubbles where it's awaited, whatever it was
      // rejected with (`breakPromise()` gives no reason, host code may
      // give any).
      p.then(
        (result) => { generator.next(result) },
        (error) => {
          generator.throw(isBubble(error) ? error : new Bubble("broken promise"));
        },
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
  breakPromise(reason) {
    this.reject(reason);
  }
}

// We might customize this in the future, but actual global console works today.
export const globalConsole = console;
