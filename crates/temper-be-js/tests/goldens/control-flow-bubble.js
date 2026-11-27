import {
  ParseError,
  divIntInt,
  failureBase,
  modIntInt,
  panic,
  stringToInt32,
} from "@temperlang/core";

/**
 * @param {number | null} x
 * @returns {number}
 * @throws {Missing}
 */
function more(x) {
  throw new Missing();
}

/**
 * @param {string | null} x
 * @returns {string}
 * @throws {Missing}
 */
function something(x) {
  {
    if (x != null) {
      return x;
    } else {
      throw new Missing();
    }
  }
}

/** @param {string | null} x */
function report(x) {
  /** @type {string} */
  let message;
  try {
    message = something(x);
  } catch (e$) {
    if (!(e$ instanceof Missing)) throw e$;
    message = "null";
  }
  /** @type {string} */
  let suffix;
  try {
    suffix = more(x != null ? 1 : 0).toString();
  } catch (e$) {
    if (!(e$ instanceof Missing)) throw e$;
    suffix = "";
  }
  console.log(message + suffix);
}

/**
 * @param {number} cols
 * @param {number} total
 * @returns {number}
 * @throws {Missing}
 */
function calcRows(cols, total) {
  console.log(`calcRows(${cols}, ${total})`);
  if (modIntInt(total, cols) !== 0) {
    throw new Missing();
  }
  return divIntInt(total, cols);
}

/**
 * @param {string} s
 * @returns {number}
 * @throws {ParseError}
 */
function parsed(s) {
  return stringToInt32(s, null);
}

/**
 * @param {string} s
 * @returns {number}
 * @throws {ParseError}
 */
function countBelow(s) {
  let i = 0;
  while (i < parsed(s)) {
    i = (i + 1) | 0;
  }
  return i;
}

/**
 * @param {number} n
 * @returns {number}
 * @throws {Missing}
 */
function firstOver(n) {
  let i = 0;
  while (i < 9) {
    i = (i + 1) | 0;
    if (i > n) {
      break;
    }
  }
  return i;
}

/**
 * @param {number} n
 * @returns {number}
 * @throws {Missing}
 */
function sumSkipping(n) {
  let sum = 0;
  {
    let i = 0;
    while (i < 5) {
      if (i === n) {
        {
          i = (i + 1) | 0;
          continue;
        }
      }
      sum = (sum + i) | 0;
      i = (i + 1) | 0;
    }
  }
  return sum;
}

/**
 * @param {number} n
 * @returns {number}
 * @throws {Missing}
 */
function tenTimes(n) {
  {
    let i = 0;
    while (i < 9) {
      if (i === n) {
        return Math.imul(i, 10);
      }
      i = (i + 1) | 0;
    }
  }
  throw new Missing();
}

/**
 * @param {number | null} r
 * @returns {boolean}
 */
function isNone(r) {
  return r == null;
}

/**
 * @param {number} n
 * @returns {string}
 */
function viaOrElse(n) {
  /** @type {number} */
  let t$0;
  try {
    t$0 = firstOver(n);
  } catch (e$) {
    if (!(e$ instanceof Missing)) throw e$;
    t$0 = -1;
  }
  /** @type {number} */
  let t$1;
  try {
    t$1 = sumSkipping(n);
  } catch (e$) {
    if (!(e$ instanceof Missing)) throw e$;
    t$1 = -1;
  }
  /** @type {number} */
  let t$2;
  try {
    t$2 = tenTimes(n);
  } catch (e$) {
    if (!(e$ instanceof Missing)) throw e$;
    t$2 = -1;
  }
  return `${t$0} ${t$1} ${t$2}`;
}

/** @returns {never} */
function unreachableInt() {
  panic();
}

/**
 * @template T
 * @param {T} fallback
 * @returns {never}
 * @throws {Missing}
 */
function failAs(fallback) {
  throw new Missing();
}

/**
 * @returns {never}
 * @throws {Missing}
 */
function bail() {
  throw new Missing();
}

/** @returns {string | null} */
function noName() {
  return null;
}

/** @returns {never} */
function stop() {
  panic();
}

/**
 * @param {number} n
 * @returns {number}
 * @throws {Missing}
 */
function half(n) {
  if (modIntInt(n, 2) !== 0) {
    bail();
  }
  if (n < 0) {
    return failAs(n);
  } else {
    return (n / 2) | 0;
  }
}

/**
 * @param {boolean} b
 * @returns {number}
 */
function pick(b) {
  if (b) {
    return 1;
  } else {
    return unreachableInt();
  }
}

/**
 * @param {boolean} b
 * @returns {string | null}
 */
function named(b) {
  if (b) {
    return "named";
  } else {
    return noName();
  }
}

/**
 * @param {boolean} b
 * @returns {number}
 */
function chosen(b) {
  /** @type {number} */
  let x;
  if (b) {
    x = 3;
    console.log("chosen");
  } else {
    unreachableInt();
  }
  return x;
}

/**
 * @param {string | null} s
 * @returns {string}
 */
function shout(s) {
  if (s == null) {
    stop();
  }
  const t = s;
  return `${t}!`;
}

/**
 * @param {number} a
 * @param {number} b
 */
function branch(a, b) {
  const c = a === 0 ? b === 0 ? "00" : b === 1 ? "01" : b === 2 ? "02" : "0x" : a === 1 ? "1x" : a === 2 ? "2x" : a === 3 ? "3x" : "xx";
  console.log(`branch(${a}, ${b}) -> ${c}`);
}

class Problem extends Error {}

failureBase(Problem);

class Missing extends Problem {
  /** @returns {string} */
  get message() {
    return "missing";
  }
}

report("hi");
report(null);
console.log(calcRows(3, 6).toString());
/** @type {number} */
let t$3;
try {
  t$3 = countBelow("abc");
} catch (e$) {
  if (!(e$ instanceof ParseError)) throw e$;
  t$3 = -1;
}
console.log(t$3.toString());
/** @type {number} */
let t$4;
try {
  t$4 = firstOver(1);
} catch (e$) {
  if (!(e$ instanceof Missing)) throw e$;
  t$4 = -1;
}
/** @type {number | null} */
let t$5;
try {
  t$5 = firstOver(1);
} catch (e$) {
  if (!(e$ instanceof Missing)) throw e$;
  t$5 = null;
}
console.log(`${t$4} ${isNone(t$5)}`);
console.log(viaOrElse(2));
console.log(viaOrElse(12));
console.log(`${pick(true)} ${named(true) ?? "-"} ${named(false) ?? "null"}`);
/** @type {number} */
let t$6;
try {
  t$6 = half(4);
} catch (e$) {
  if (!(e$ instanceof Missing)) throw e$;
  t$6 = -1;
}
/** @type {number} */
let t$7;
try {
  t$7 = half(3);
} catch (e$) {
  if (!(e$ instanceof Missing)) throw e$;
  t$7 = -1;
}
/** @type {number} */
let t$8;
try {
  t$8 = half(-2);
} catch (e$) {
  if (!(e$ instanceof Missing)) throw e$;
  t$8 = -1;
}
console.log(`${t$6} ${t$7} ${t$8}`);
console.log(`${chosen(true)} ${shout("hey")}`);
branch(0, 2);
branch(2, 0);
