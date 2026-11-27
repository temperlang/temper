// @ts-check

/**
 * @param {number} x
 * @param {number} y
 * @returns {number}
 */
export const divIntInt = (x, y) => {
  const result = Math.trunc(x / y);
  if (!Number.isSafeInteger(result)) {
    panic("division by zero");
  }
  /* not NaN or infinite */
  return result | 0;
};

/**
 * @param {number} x
 * @param {number} y
 * @returns {number}
 */
export const modIntInt = (x, y) => {
  const result = Math.trunc(x % y);
  // TODO: is this ever false if `Number.isSafeInteger(x)`?
  if (!Number.isSafeInteger(result)) {
    panic("division by zero");
  }
  /* not NaN or infinite */
  return result | 0;
};

/**
 * The integer `s` spells in `radix`, or null if it isn't one.
 * @param {string} s
 * @param {(number | bigint)?} radix
 * @returns {bigint | null}
 */
export const parseBigInt = (s, radix) => {
    const rdx = BigInt(radix ?? 10);
    let result = 0n;
    // TODO Definitions of whitespace? Better to check /\s/ on the fly?
    s = s.trim();
    let i = 0;
    let sign = 1n;
    if (s.startsWith("-")) {
      i++;
      sign = -1n;
    }
    if (i === s.length) {
      return null;
    }
    for (; i < s.length; i++) {
      const c = s.charCodeAt(i);
      /** @type {bigint} */
      let digit;
      if (c >= c0 && c <= c9) {
        digit = BigInt(c - c0);
      } else if (c >= cA && c <= cZ) {
        digit = BigInt(c - cA + 10);
      } else if (c >= ca && c <= cz) {
        digit = BigInt(c - ca + 10);
      } else {
        return null;
      }
      if (digit >= rdx) {
        return null;
      }
      result = result * rdx + digit;
    }
    return sign * result;
};

const c0 = "0".charCodeAt(0);
const c9 = "9".charCodeAt(0);
const cA = "A".charCodeAt(0);
const cZ = "Z".charCodeAt(0);
const ca = "a".charCodeAt(0);
const cz = "z".charCodeAt(0);

export const INT32_MAX = (2 ** 31) - 1;
export const INT32_MIN = -(2 ** 31);
export const INT64_MAX = (2n ** 63n) - 1n;
export const INT64_MIN = -(2n ** 63n);

// Related to int64 limits above.
const edge64 = 1n << 64n;

/**
 * @param {bigint} x
 * @returns {bigint}
 */
export const clampInt64 = (x) => {
  x = x % edge64;
  if (x > INT64_MAX) {
    x -= edge64;
  } else if (x < INT64_MIN) {
    x += edge64;
  }
  return x;
};

/**
 * `a / b`, truncated, wrapped to Int64, panicking on a zero divisor,
 * where BigInt's `/` throws a `RangeError`.
 * @param {bigint} a
 * @param {bigint} b
 * @returns {bigint}
 */
export const divInt64 = (a, b) => {
  if (b === 0n) {
    panic("division by zero");
  }
  return clampInt64(a / b);
};

/**
 * `a % b`, panicking on a zero divisor.
 * @param {bigint} a
 * @param {bigint} b
 * @returns {bigint}
 */
export const modInt64 = (a, b) => {
  if (b === 0n) {
    panic("division by zero");
  }
  return a % b;
};

/**
 * Implements extension method `core.type Int64.max()`
 * @param {bigint} a
 * @param {bigint} b
 * @returns {bigint}
 */
export const int64Max = (a, b) => {
  return a > b ? a : b;
}

/**
 * Implements extension method `core.type Int64.min()`
 * @param {bigint} a
 * @param {bigint} b
 * @returns {bigint}
 */
export const int64Min = (a, b) => {
  return a < b ? a : b;
}

/**
 * Implements extension method `core.type Int64.toInt32()`
 * @param {bigint} n
 * @returns {number}
 */
export const int64ToInt32 = (n) => {
  if (n < INT32_MIN || n > INT32_MAX) {
    throw new ParseError(n.toString(), "out of Int32 range");
  }
  return int64ToInt32Unsafe(n);
}

/**
 * Implements extension method `core.type Int64.toInt32Unsafe()`
 * @param {bigint} n
 * @returns {number}
 */
export const int64ToInt32Unsafe = (n) => {
  return Number(n) | 0;
}

/**
 * Implements extension method `core.type Int64.toFloat64()`
 * @param {bigint} n
 * @returns {number}
 */
export const int64ToFloat64 = (n) => {
  if (n < Number.MIN_SAFE_INTEGER || n > Number.MAX_SAFE_INTEGER) {
    throw new ParseError(n.toString(), "not exact as Float64");
  }
  return Number(n);
}

/**
 * Implements extension method `core.type Int64.toFloat63Unsafe()`
 * @param {bigint} n
 * @returns {number}
 */
export const int64ToFloat64Unsafe = (n) => {
  return Number(n);
}

/**
 * Ternary comparison of two strings lexicographically by code-point,
 * not UTF-16.
 * @param {string} a
 * @param {string} b
 * @return {number}
 */
export const cmpString = (a, b) => {
  if (Object.is(a, b)) {
    return 0;
  }
  const aLen = a.length;
  const bLen = b.length;
  const minLen = aLen < bLen ? aLen : bLen;
  for (let i = 0; i < minLen;) {
    const ca = a.codePointAt(i);
    const cb = b.codePointAt(i);
    const d = ca - cb;
    if (d !== 0) {
      return d;
    }
    i += ca < 0x10000 ? 1 : 2;
  }
  return aLen - bLen;
};

/**
 * Ternary comparison of two numbers, treating -0 as less than +0,
 * and treating all `NaN`s as equivalent per Temper Float64 semantics.
 * @param {number} a
 * @param {number} b
 * @return {number}
 */
export const cmpFloat64 = (a, b) => {
  if (Object.is(a, b)) {
    return 0;
  }
  if (a === b) {
    // @ts-ignore
    return Object.is(a, 0) - Object.is(b, 0);
  }
  if (isNaN(a) || isNaN(b)) {
    // @ts-ignore
    return isNaN(a) - isNaN(b);
  }
  return a - b;
};

/**
 * Equality check consistent with cmpFloat64.
 * @param {number} a
 * @param {number} b
 * @return {boolean}
 */
export const eqFloat64 = (a, b) => cmpFloat64(a, b) === 0

/**
 * Ternary comparison of two Numbers representing Temper Int32s
 * which are in the JS safe-integer range.
 * @param {number} a
 * @param {number} b
 * @return {number}
 */
export const cmpInt32 = (a, b) => Math.sign(a - b);

/**
 * Ternary comparison of two `bigint`s representing Temper Int64s.
 * @param {bigint} a
 * @param {bigint} b
 * @return {-1 | 0 | 1}
 */
export const cmpInt64 = (a, b) =>
  a < b ? -1 : a === b ? 0 : 1;

/**
 * Compare two Booleans ordering false before true.
 * @param {number} a
 * @param {number} b
 * @return {number}
 */
export const cmpBoolean = (a, b) => +a - +b;

/** What marks a declared failure, the same in every copy of this runtime. */
const failureMark = Symbol.for("@temperlang/core.Failure");

/**
 * Makes `base` one of a library's root failure classes (RFC 164's
 * declared failures): generated code declares it as `class WherewithError
 * extends Error {}`, with its failure types below it, and calls this once. Its
 * instances get the marker `isFailure` reads, which host code never
 * needs, and `name`, the class's own name, as `Error`'s says what it is
 * (`NotFound: not found (key: y)`). A failure type's own member named
 * `name` is `name_` in JS, so it doesn't hide this one.
 *
 * @param {Function} base
 */
export const failureBase = (base) => {
  Object.defineProperty(base.prototype, failureMark, { value: true });
  Object.defineProperty(base.prototype, "name", {
    configurable: true,
    get() {
      return this.constructor.name;
    },
  });
};

/**
 * The root of core's own declared failures, `MissingKey`, `ParseError`,
 * `InvalidCodePoint`, and `BrokenPromise`, as each library's roots are
 * its failures'.
 */
export class CoreError extends Error {}

failureBase(CoreError);

/**
 * What a map's `get` throws for a key it doesn't have, with that key.
 * (Later perhaps a `RangeError` too, or what hosts' lookups throw.)
 */
export class MissingKey extends CoreError {
  /** @type {unknown} */
  #key;

  /** @param {unknown} key */
  constructor(key) {
    super();
    this.#key = key;
  }

  /** @returns {unknown} */
  get key() {
    return this.#key;
  }

  /** @returns {string} */
  get message() {
    return `missing key (key: ${this.#key})`;
  }
}

/**
 * What a conversion throws for a value it can't convert: its input, as
 * text, and why, as `not an Int32`.
 */
export class ParseError extends CoreError {
  /** @type {string} */
  #input;
  /** @type {string} */
  #reason;

  /**
   * @param {string} input
   * @param {string} reason
   */
  constructor(input, reason) {
    super();
    this.#input = input;
    this.#reason = reason;
  }

  /** @returns {string} */
  get input() {
    return this.#input;
  }

  /** @returns {string} */
  get reason() {
    return this.#reason;
  }

  /** @returns {string} */
  get message() {
    return `parse error (input: ${this.#input}, reason: ${this.#reason})`;
  }
}

/**
 * What making a string from a code point throws for one that isn't a
 * Unicode scalar value, with that number. (Later perhaps a `RangeError`
 * too, as `String.fromCodePoint` throws.)
 */
export class InvalidCodePoint extends CoreError {
  /** @type {number} */
  #codePoint;

  /** @param {number} codePoint */
  constructor(codePoint) {
    super();
    this.#codePoint = codePoint;
  }

  /** @returns {number} */
  get codePoint() {
    return this.#codePoint;
  }

  /** @returns {string} */
  get message() {
    return `invalid code point (codePoint: ${this.#codePoint})`;
  }
}

/**
 * What awaiting a broken promise throws. It has no fields: a broken
 * promise doesn't say why.
 */
export class BrokenPromise extends CoreError {
  /** @returns {string} */
  get message() {
    return "broken promise";
  }
}

/**
 * Whether `e` is a declared failure, made by any copy of this runtime:
 * what generated code catches of failures of several libraries.
 *
 * @param {unknown} e
 * @returns {e is Error}
 */
export const isFailure = (e) =>
  typeof e === "object" && e !== null && failureMark in e;

/**
 * A declared failure's cause, as Temper's `Error.cause` gives it: the
 * failure that was being handled where it was thrown, or `null`, also
 * where that was something else, such as a host's error.
 *
 * @param {Error} failure
 * @returns {Error | null}
 */
export const failureCause = (failure) => {
  const cause = failure.cause;
  return isFailure(cause) ? /** @type {Error} */ (cause) : null;
};

/**
 * `failure`, with `cause` as its standard `cause`, for a Temper `throw`
 * in a handler, unless it has a cause already: a failure thrown again
 * keeps the cause it was first thrown with. Never itself, nor a loop of
 * causes. The property is as `new Error(message, { cause })` makes it,
 * which a generated constructor, taking its type's own parameters,
 * can't be given.
 *
 * @template {Error} T
 * @param {T} failure
 * @param {unknown} cause
 * @returns {T}
 */
export const withCause = (failure, cause) => {
  if (Object.hasOwn(failure, "cause")) {
    return failure;
  }
  const seen = new Set();
  for (let c = cause; c instanceof Error && !seen.has(c); c = c.cause) {
    if (c === failure) {
      return failure;
    }
    seen.add(c);
  }
  Object.defineProperty(failure, "cause", {
    configurable: true,
    writable: true,
    value: cause,
  });
  return failure;
};

/**
 * Throws a plain `Error`, which no `orelse` catches, with `cause` as its
 * cause if given: the failure an `orelse panic()` replaces.
 *
 * @param {string} [message]
 * @param {unknown} [cause]
 * @returns {never}
 */
export const panic = (message = "panic", cause = undefined) => {
  throw cause === undefined ? new Error(message) : new Error(message, { cause });
};

/**
 * Runs `block`, giving whether it panicked, for std's `Test.assertPanics`:
 * whether it threw anything but a declared failure or a test
 * framework's failure (an `AssertionError`, as Node's `assert` and Chai
 * throw), as on every backend, so `panic`'s `Error` or a host's error,
 * such as a `TypeError`. What isn't passes through.
 *
 * @param {() => void} block
 * @returns {boolean}
 */
export const blockPanics = (block) => {
  try {
    block();
  } catch (e) {
    if (isFailure(e) || (e instanceof Error && e.name === "AssertionError")) {
      throw e;
    }
    return true;
  }
  return false;
};

/**
 * @template T
 * @param {T} a
 */
export const print = (a) => {
  console.log("%s", a);
};

/**
 * Takes a JSON adapter and a value that it can adapt.
 * This is called when JavaScript code calls JSON.stringify on a Temper type instance
 * that has a zero-argument jsonAdapter static method.
 *
 * @return any
 */
export const marshalToJsonObject = (jsonAdapter, value) => {
  /** @type {any[]} */
  const stack = [[]];
  let pendingKey = null;
  function store(value) {
    let top = stack[stack.length - 1];
    if (pendingKey !== null) {
      top[pendingKey] = value;
      pendingKey = null;
    } else if (Array.isArray(top)) {
      top.push(value);
    } else {
      throw new Error();
    }
  }
  let jsonProducer = {
    interchangeContext: { getHeader() { return null; } },

    startObject() {
      let o = {};
      store(o);
      stack.push(o);
    },
    endObject() { stack.pop(); },
    objectKey(key) { pendingKey = String(key); },

    startArray() {
      let a = [];
      store(a);
      stack.push(a);
    },
    endArray() { stack.pop(); },

    nullValue() { store(null); },
    booleanValue(b) { store(!!b); },
    int32Value(v) { store(Math.trunc(v)); },
    float64Value(v) { store(+v); },
    numericTokenValue(s) { store(+s); },
    stringValue(s) { store(`${s}`); },

    parseErrorReceiver: null,
  }
  jsonAdapter.encodeToJson(value, jsonProducer);
  return stack[0][0];
};

/** @type {{}} */
let emptySingleton = Object.freeze(
  // Prototype for empty
  Object.create(
    Object.freeze(
      Object.create(
        null,
        {
          toString: {
            value: function toString() { return "(empty)" }
          }
        }
      )
    )
  )
);

/** @return {{}} */
export function empty() { return emptySingleton }
