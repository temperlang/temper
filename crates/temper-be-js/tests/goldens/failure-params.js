import {
  failureBase,
  isFailure,
  listBuilderToList,
  listedFilter,
  listedGet,
  listedJoin,
  listedMap,
  listedReduceFrom,
  listedSorted,
  panic,
} from "@temperlang/core";

/**
 * @param {string} key
 * @returns {number}
 * @throws {Missing}
 */
function find(key) {
  if (key === "one") {
    return 1;
  }
  if (key === "two") {
    return 2;
  }
  throw new Missing(key);
}

/**
 * @template T
 * @template R
 * @param {readonly T[]} xs
 * @param {(arg0: T) => R} f
 * @returns {readonly R[]}
 */
function mapAll(xs, f) {
  /** @type {R[]} */
  const out = [];
  {
    const length = xs.length;
    let i = 0;
    while (i < length) {
      const x = listedGet(/** @type {T[]} */ (xs), i);
      i = (i + 1) | 0;
      out.push(f(x));
    }
  }
  return listBuilderToList(out);
}

/**
 * @param {readonly string[]} keys
 * @returns {readonly number[]}
 * @throws {Missing}
 */
function found(keys) {
  return mapAll(keys, (k) => find(k));
}

/**
 * @param {readonly string[]} keys
 * @returns {string}
 */
function shown(keys) {
  /** @type {readonly number[]} */
  let ns;
  /** @type {Missing | null} */
  let caught = null;
  /** @type {readonly number[] | null} */
  let held = null;
  try {
    held = found(keys);
  } catch (failure) {
    if (!(failure instanceof Missing)) throw failure;
    caught = failure;
  }
  if (held == null) {
    const e = /** @type {Missing} */ (caught);
    return `missing ${e.key}`;
  } else {
    ns = held;
  }
  return listedJoin(/** @type {number[]} */ (ns), ",", (n) => n.toString());
}

/**
 * @param {(arg0: number) => number} f
 * @param {number} n
 * @returns {number}
 * @throws {Missing}
 */
function applied(f, n) {
  return f(n);
}

/**
 * @template T
 * @template R
 * @param {readonly T[]} xs
 * @param {(arg0: T) => R} f
 * @returns {number}
 */
function failures(xs, f) {
  let failed = 0;
  {
    const length = xs.length;
    let i = 0;
    while (i < length) {
      const x = listedGet(/** @type {T[]} */ (xs), i);
      i = (i + 1) | 0;
      try {
        f(x);
      } catch (e$) {
        if (!isFailure(e$)) throw e$;
        failed = (failed + 1) | 0;
      }
    }
  }
  return failed;
}

/**
 * @template T
 * @param {readonly T[]} xs
 * @param {(arg0: T) => number} f
 * @returns {number}
 */
function sumAll(xs, f) {
  let total = 0;
  /** @type {Error | null} */
  let first = null;
  {
    const length = xs.length;
    let i = 0;
    while (i < length) {
      const x = listedGet(/** @type {T[]} */ (xs), i);
      i = (i + 1) | 0;
      try {
        total = (total + f(x)) | 0;
      } catch (e) {
        if (!isFailure(e)) throw e;
        if (first == null) {
          first = e;
        }
      }
    }
  }
  const failed = first;
  if (failed != null) {
    throw failed;
  }
  return total;
}

/**
 * @param {(arg0: string) => number} a
 * @param {(arg0: string) => number} b
 * @param {string} key
 * @returns {number}
 */
function both(a, b, key) {
  return (a(key) + b(key)) | 0;
}

/**
 * @param {string} key
 * @returns {number}
 * @throws {Unknown}
 */
function word(key) {
  if (key === "ten") {
    return 10;
  }
  throw new Unknown(key);
}

/**
 * @param {string} key
 * @returns {string}
 */
function either(key) {
  /** @type {number} */
  let n;
  /** @type {FailureParamsError | null} */
  let caught = null;
  /** @type {number | null} */
  let held = null;
  try {
    held = both((x) => find(x), (x2) => word(x2), key);
  } catch (failure) {
    if (!(failure instanceof FailureParamsError)) throw failure;
    caught = failure;
  }
  if (held == null) {
    const e = /** @type {FailureParamsError} */ (caught);
    return e instanceof Missing ? `no ${/** @type {Missing} */ (e).key}` : e instanceof Unknown ? `unknown ${/** @type {Unknown} */ (e).word}` : panic();
  } else {
    n = held;
  }
  return n.toString();
}

/**
 * @param {readonly string[]} keys
 * @returns {string}
 * @throws {Missing}
 */
function parts(keys) {
  /** @type {readonly string[]} */
  const found = listedFilter(/** @type {string[]} */ (keys), (k) => find(k) > 1);
  const total = listedReduceFrom(/** @type {string[]} */ (keys), 0, (n, k2) => (n + find(k2)) | 0);
  return `${listedJoin(/** @type {string[]} */ (found), ",", (k3) => k3)} ${total} ${listedJoin(/** @type {string[]} */ (keys), "+", (k4) => find(k4).toString())}`;
}

/**
 * @template T
 * @param {readonly T[]} xs
 * @param {(arg0: T) => T} f
 * @returns {readonly T[]}
 */
function mapTwice(xs, f) {
  /** @type {readonly T[]} */
  const once = mapAll(xs, f);
  return listedMap(/** @type {T[]} */ (once), f);
}

/**
 * @param {number} n
 * @returns {number}
 * @throws {Missing}
 */
function upToTwo(n) {
  if (n > 2) {
    throw new Missing(n.toString());
  }
  return (n + 1) | 0;
}

class FailureParamsError extends Error {}

failureBase(FailureParamsError);

class Missing extends FailureParamsError {
  /** @type {string} */
  #key;

  /** @param {string} key */
  constructor(key) {
    super();
    this.#key = key;
  }

  /** @returns {string} */
  get key() {
    return this.#key;
  }

  /** @returns {string} */
  get message() {
    return `missing (key: ${this.#key})`;
  }
}

class Unknown extends FailureParamsError {
  /** @type {string} */
  #word;

  /** @param {string} word */
  constructor(word) {
    super();
    this.#word = word;
  }

  /** @returns {string} */
  get word() {
    return this.#word;
  }

  /** @returns {string} */
  get message() {
    return `unknown (word: ${this.#word})`;
  }
}

/** @type {readonly number[]} */
const doubled = mapAll(Object.freeze([1, 2, 3]), (n) => Math.imul(n, 2));
/** @type {(arg0: number) => number} */
const safe = (n) => (n + 1) | 0;
console.log(`${listedJoin(/** @type {number[]} */ (doubled), ",", (n) => n.toString())} ${safe(1)}`);
/** @type {readonly number[]} */
let named;
try {
  named = mapAll(Object.freeze(["two", "one"]), find);
} catch (e$) {
  if (!(e$ instanceof Missing)) throw e$;
  named = Object.freeze([]);
}
console.log(`${shown(Object.freeze(["one", "two"]))} ${shown(Object.freeze(["one", "six", "ten"]))} ${named.length}`);
/** @type {number} */
let six;
try {
  six = applied((n) => Math.imul(n, 3), 2);
} catch (e$) {
  if (!(e$ instanceof Missing)) throw e$;
  six = -1;
}
console.log(six.toString());
const some = failures(Object.freeze(["one", "six", "two", "ten"]), find);
const none = failures(Object.freeze([1, 2]), (n) => n);
console.log(`${some} ${none}`);
/** @type {number} */
let summed;
try {
  summed = sumAll(Object.freeze(["one", "two"]), find);
} catch (e$) {
  if (!(e$ instanceof Missing)) throw e$;
  summed = -1;
}
/** @type {number} */
let unsummed;
try {
  unsummed = sumAll(Object.freeze(["six", "one", "ten"]), find);
} catch (e) {
  if (!(e instanceof Missing)) throw e;
  console.log(`first missing ${e.key}`);
  unsummed = -1;
}
console.log(`${summed} ${unsummed} ${sumAll(Object.freeze([1, 2]), safe)}`);
/** @type {number} */
let twelve;
try {
  twelve = both(find, (k) => 10, "two");
} catch (e$) {
  if (!(e$ instanceof Missing)) throw e$;
  twelve = -1;
}
/** @type {number} */
let lost;
try {
  lost = both(find, find, "six");
} catch (e2) {
  if (!(e2 instanceof Missing)) throw e2;
  console.log(`lost ${e2.key}`);
  lost = -1;
}
console.log(`${twelve} ${lost}`);
console.log(`${either("one")} ${either("ten")}`);
/** @type {string} */
let t$0;
try {
  t$0 = parts(Object.freeze(["one", "two"]));
} catch (e$) {
  if (!(e$ instanceof Missing)) throw e$;
  t$0 = "?";
}
console.log(t$0);
/** @type {string} */
let t$1;
try {
  t$1 = parts(Object.freeze(["one", "six"]));
} catch (e3) {
  if (!(e3 instanceof Missing)) throw e3;
  t$1 = `missing ${e3.key}`;
}
console.log(t$1);
console.log(listedJoin(/** @type {number[]} */ (listedSorted(/** @type {number[]} */ (Object.freeze([3, 1, 2])), (a, b) => (a - b) | 0)), ",", (n) => n.toString()));
/** @type {readonly number[]} */
const bumped = listedMap(/** @type {number[]} */ (Object.freeze([1, 2])), safe);
/** @type {readonly number[]} */
const twice = mapTwice(Object.freeze([1, 2]), safe);
/** @type {readonly number[]} */
let failed;
try {
  failed = mapTwice(Object.freeze([1, 2]), upToTwo);
} catch (e4) {
  if (!(e4 instanceof Missing)) throw e4;
  console.log(`missing ${e4.key}`);
  failed = Object.freeze([]);
}
console.log(`${listedJoin(/** @type {number[]} */ (bumped), ",", (n) => n.toString())} ${listedJoin(/** @type {number[]} */ (twice), ",", (n) => n.toString())} ${failed.length}`);
