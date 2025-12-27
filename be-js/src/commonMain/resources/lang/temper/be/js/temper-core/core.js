// @ts-check

/**
 * @param {number} x
 * @param {number} y
 * @returns {number}
 */
export const divIntInt = (x, y) => {
  const result = Math.trunc(x / y);
  if (!Number.isSafeInteger(result)) {
    bubble();
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
    bubble();
  }
  /* not NaN or infinite */
  return result | 0;
};

/**
 * @param {string} s
 * @param {(number | bigint)?} radix
 * @returns {bigint}
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
        bubble();
      }
      if (digit >= rdx) {
        bubble();
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
 * Implements extension method Int64::max
 * @param {bigint} a
 * @param {bigint} b
 * @returns {bigint}
 */
export const int64Max = (a, b) => {
  return a > b ? a : b;
}

/**
 * Implements extension method Int64::min
 * @param {bigint} a
 * @param {bigint} b
 * @returns {bigint}
 */
export const int64Min = (a, b) => {
  return a < b ? a : b;
}

/**
 * Implements extension method Int64::toInt32
 * @param {bigint} n
 * @returns {number}
 */
export const int64ToInt32 = (n) => {
  if (n < INT32_MIN || n > INT32_MAX) {
    bubble();
  }
  return int64ToInt32Unsafe(n);
}

/**
 * Implements extension method Int64::toInt32Unsafe
 * @param {bigint} n
 * @returns {number}
 */
export const int64ToInt32Unsafe = (n) => {
  return Number(n) | 0;
}

/**
 * Implements extension method Int64::toFloat64
 * @param {bigint} n
 * @returns {number}
 */
export const int64ToFloat64 = (n) => {
  if (n < Number.MIN_SAFE_INTEGER || n > Number.MAX_SAFE_INTEGER) {
    bubble();
  }
  return Number(n);
}

/**
 * Implements extension method Int64::toFloat63Unsafe
 * @param {bigint} n
 * @returns {number}
 */
export const int64ToFloat64Unsafe = (n) => {
  return Number(n);
}

/**
 * Compare two Strings.
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
 * Compare two Numbers, accounting for signedness of zero.
 * @param {number} a
 * @param {number} b
 * @return {number}
 */
export const cmpFloat = (a, b) => {
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
 * @template {string | number | boolean} T
 * @param {T} a
 * @param {T} b
 * @returns {number}
 */
export const cmpGeneric = (a, b) => {
  if (typeof a === "string" && typeof b === "string") {
    return cmpString(a, b);
  }
  if (typeof a === "number" && typeof b === "number") {
    return cmpFloat(a, b);
  }
  if (typeof a === "boolean" && typeof b === "boolean") {
    // @ts-ignore
    return a - b;
  }
  bubble();
};

/**
 * @returns {never}
 */
export const bubble = () => {
  throw Error();
};

/**
 * TODO Distinguish panic from bubble.
 *
 * @returns {never}
 */
export const panic = bubble;

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
 * that has a zero argument jsonAdapter static method.
 *
 * @return any
 */
export let marshalToJsonObject = (jsonAdapter, value) => {
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
