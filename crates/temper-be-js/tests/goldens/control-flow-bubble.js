import {
  bubble,
  divIntInt,
  modIntInt,
  panic,
  requireNotNull,
  stringToInt32,
} from "@temperlang/core";

/**
 * @param {number | null} x
 * @returns {number}
 */
function more(x) {
  bubble();
}

/**
 * @param {string | null} x
 * @returns {string}
 */
function something(x) {
  {
    if (x != null) {
      return x;
    } else {
      bubble();
    }
  }
}

/** @param {string | null} x */
function report(x) {
  /** @type {string} */
  let message;
  try {
    message = something(x);
  } catch {
    message = "null";
  }
  /** @type {string} */
  let suffix;
  try {
    /** @type {number} */
    let t$0;
    try {
      requireNotNull(x);
      t$0 = 1;
    } catch {
      t$0 = 0;
    }
    suffix = more(t$0).toString();
  } catch {
    suffix = "";
  }
  console.log(message + suffix);
}

/**
 * @param {number} cols
 * @param {number} total
 * @returns {number}
 */
function calcRows(cols, total) {
  console.log(`calcRows(${cols}, ${total})`);
  if (modIntInt(total, cols) !== 0) {
    bubble();
  }
  return divIntInt(total, cols);
}

/**
 * @param {string} s
 * @returns {number}
 */
function countBelow(s) {
  let i = 0;
  while (i < stringToInt32(s, null)) {
    i = (i + 1) | 0;
  }
  return i;
}

/**
 * @param {number} n
 * @returns {number}
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
  bubble();
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
  let t$1;
  try {
    t$1 = firstOver(n);
  } catch {
    t$1 = -1;
  }
  /** @type {number} */
  let t$2;
  try {
    t$2 = sumSkipping(n);
  } catch {
    t$2 = -1;
  }
  /** @type {number} */
  let t$3;
  try {
    t$3 = tenTimes(n);
  } catch {
    t$3 = -1;
  }
  return `${t$1} ${t$2} ${t$3}`;
}

/** @returns {never} */
function unreachableInt() {
  panic();
}

/**
 * @template T
 * @param {T} fallback
 * @returns {never}
 */
function failAs(fallback) {
  bubble();
}

/** @returns {never} */
function bail() {
  bubble();
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

report("hi");
report(null);
console.log(calcRows(3, 6).toString());
/** @type {number} */
let t$4;
try {
  t$4 = countBelow("abc");
} catch {
  t$4 = -1;
}
console.log(t$4.toString());
/** @type {number} */
let t$5;
try {
  t$5 = firstOver(1);
} catch {
  t$5 = -1;
}
/** @type {number | null} */
let t$6;
try {
  t$6 = firstOver(1);
} catch {
  t$6 = null;
}
console.log(`${t$5} ${isNone(t$6)}`);
console.log(viaOrElse(2));
console.log(viaOrElse(12));
console.log(`${pick(true)} ${named(true) ?? "-"} ${named(false) ?? "null"}`);
/** @type {number} */
let t$7;
try {
  t$7 = half(4);
} catch {
  t$7 = -1;
}
/** @type {number} */
let t$8;
try {
  t$8 = half(3);
} catch {
  t$8 = -1;
}
/** @type {number} */
let t$9;
try {
  t$9 = half(-2);
} catch {
  t$9 = -1;
}
console.log(`${t$7} ${t$8} ${t$9}`);
console.log(`${chosen(true)} ${shout("hey")}`);
branch(0, 2);
branch(2, 0);
