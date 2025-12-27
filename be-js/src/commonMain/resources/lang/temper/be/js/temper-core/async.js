
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
      p.then(
        (result) => { generator.next(result) },
        (error) => { generator.throw(error) },
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
  }

  /** @param {R} value */
  complete(value) {
    this.resolve(value)
  }

  /** @param {any?} reason */
  breakPromise(reason) {
    this.reject(reason);
  }
}

// We might customize this in the future, but actual global console works today.
export const globalConsole = console;
