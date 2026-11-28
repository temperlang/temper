import {
  BrokenGenerator,
  adaptAwaiter,
  failureBase,
  failureCause,
  generatorNext,
  listedJoin,
} from "@temperlang/core";

/**
 * @template T
 * @param {() => Generator<T>} make
 * @returns {Generator<T>}
 */
function generated(make) {
  return make();
}

/**
 * @template T
 * @param {() => Generator<T>} make
 * @returns {Generator<T>}
 */
function safely(make) {
  return make();
}

/**
 * @param {Error} e
 * @returns {string}
 */
function why(e) {
  const mine = e instanceof TooBig ? `too big ${/** @type {TooBig} */ (/** @type {unknown} */ (e)).n}` : e.message;
  const cause = failureCause(e);
  if (cause == null) {
    return mine;
  } else {
    return `${mine}: ${why(cause)}`;
  }
}

/**
 * @param {Generator<number>} g
 * @param {number} n
 * @returns {string}
 */
function take(g, n) {
  /** @type {string[]} */
  const taken = [];
  while (taken.length < n) {
    /** @type {IteratorResult<number, void>} */
    let r;
    let failed = false;
    /** @type {BrokenGenerator | null} */
    let caught = null;
    /** @type {IteratorResult<number, void> | null} */
    let held = null;
    try {
      held = generatorNext(g);
    } catch (failure) {
      if (!(failure instanceof BrokenGenerator)) throw failure;
      caught = failure;
      failed = true;
    }
    if (failed) {
      const e = /** @type {BrokenGenerator} */ (caught);
      return `${listedJoin(taken, " ", (s) => s)} ${why(/** @type {Error} */ (/** @type {unknown} */ (e)))}`;
    } else {
      r = /** @type {IteratorResult<number, void>} */ (held);
    }
    if (!r.done) {
      taken.push(/** @type {IteratorYieldResult<number>} */ (/** @type {unknown} */ (r)).value.toString());
    } else {
      taken.push("done");
      break;
    }
  }
  return listedJoin(taken, " ", (s2) => s2);
}

/**
 * @param {number} limit
 * @returns {Generator<number>}
 */
function upTo(limit) {
  /**
   * @param {(p: Promise<unknown>) => void} doAwait
   * @returns {Generator<any>}
   */
  function* fn(doAwait) {
    let i = 1;
    while (true) {
      if (i > limit) {
        throw new TooBig(i);
      }
      yield i;
      i = (i + 1) | 0;
    }
  }
  return generated(adaptAwaiter(fn));
}

/**
 * @param {number} n
 * @param {number} limit
 * @returns {number}
 * @throws {TooBig}
 */
function checked(n, limit) {
  if (n > limit) {
    throw new TooBig(n);
  }
  return n;
}

/**
 * @param {number} limit
 * @returns {Generator<number>}
 */
function squares(limit) {
  /**
   * @param {(p: Promise<unknown>) => void} doAwait
   * @returns {Generator<any>}
   */
  function* fn(doAwait) {
    /** @type {number} */
    let t$0;
    try {
      t$0 = checked(50, limit);
    } catch (e$) {
      if (!(e$ instanceof TooBig)) throw e$;
      t$0 = 0;
    }
    yield t$0;
    let i = 1;
    while (true) {
      yield checked(Math.imul(i, i), limit);
      i = (i + 1) | 0;
    }
  }
  return generated(adaptAwaiter(fn));
}

/**
 * @param {number} n
 * @returns {Generator<number>}
 */
function count(n) {
  /**
   * @param {(p: Promise<unknown>) => void} doAwait
   * @returns {Generator<any>}
   */
  function* fn(doAwait) {
    {
      let i = 1;
      while (i <= n) {
        yield i;
        i = (i + 1) | 0;
      }
    }
  }
  return safely(adaptAwaiter(fn));
}

/**
 * @param {IteratorResult<number, void>} r
 * @returns {string}
 */
function shown(r) {
  if (!r.done) {
    return /** @type {IteratorYieldResult<number>} */ (/** @type {unknown} */ (r)).value.toString();
  } else {
    return "done";
  }
}

/**
 * @param {Generator<number>} g
 * @returns {string}
 */
function firstTwo(g) {
  const a = shown(g.next());
  /** @type {string} */
  let b;
  try {
    b = shown(generatorNext(/** @type {Generator<number>} */ (/** @type {unknown} */ (g))));
  } catch (e$) {
    if (!(e$ instanceof BrokenGenerator)) throw e$;
    b = "failed";
  }
  return `${a} ${b}`;
}

/**
 * @param {Generator<number>} inner
 * @returns {Generator<number>}
 */
function doubled(inner) {
  /**
   * @param {(p: Promise<unknown>) => void} doAwait
   * @returns {Generator<any>}
   */
  function* fn(doAwait) {
    while (true) {
      /** @type {IteratorResult<number, void>} */
      const r = generatorNext(inner);
      if (!r.done) {
        yield Math.imul(/** @type {IteratorYieldResult<number>} */ (/** @type {unknown} */ (r)).value, 2);
      } else {
        break;
      }
    }
  }
  return generated(adaptAwaiter(fn));
}

class ErrorGeneratorsError extends Error {}

failureBase(ErrorGeneratorsError);

class TooBig extends ErrorGeneratorsError {
  /** @type {number} */
  #n;

  /** @param {number} n */
  constructor(n) {
    super();
    this.#n = n;
  }

  /** @returns {number} */
  get n() {
    return this.#n;
  }

  /** @returns {string} */
  get message() {
    return `too big (n: ${this.#n})`;
  }
}

console.log(take(upTo(3), 9));
/** @type {Generator<number>} */
const one = upTo(1);
console.log(take(one, 9));
console.log(take(one, 9));
console.log(take(squares(10), 9));
console.log(take(/** @type {Generator<number>} */ (/** @type {unknown} */ (count(2))), 9));
console.log(firstTwo(count(1)));
console.log(take(doubled(upTo(2)), 9));
console.log(take(doubled(/** @type {Generator<number>} */ (/** @type {unknown} */ (count(2)))), 9));
