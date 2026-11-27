import { eqFloat64, failureBase } from "@temperlang/core";

/**
 * @param {number} n
 * @returns {number}
 */
function noted(n) {
  calls = (calls + 1) | 0;
  console.log(`noted ${n}`);
  return n;
}

/**
 * @param {number} n
 * @returns {number}
 * @throws {TooBig}
 */
function failing(n) {
  if (n > 2) {
    throw new TooBig();
  }
  return n;
}

/**
 * @param {number} a
 * @param {number} b
 * @returns {string}
 */
function pair(a, b) {
  return `${a},${b}`;
}

/**
 * @param {number | null} a
 * @param {number | null} b
 * @returns {boolean}
 */
function sameFloat(a, b) {
  return a == null ? b == null : b != null && eqFloat64(a, b);
}

class Problem extends Error {}

failureBase(Problem);

class TooBig extends Problem {
  /** @returns {string} */
  get message() {
    return "too big";
  }
}

let calls = 0;
const t$1 = noted(1);
/** @type {number} */
let t$0;
try {
  t$0 = failing(3);
} catch (e$) {
  if (!(e$ instanceof TooBig)) throw e$;
  t$0 = 9;
}
console.log(pair(t$1, t$0));
const t = true;
const f = false;
let t$3 = f;
if (t$3) {
  /** @type {number} */
  let t$2;
  try {
    t$2 = failing(5);
  } catch (e$) {
    if (!(e$ instanceof TooBig)) throw e$;
    t$2 = 1;
  }
  t$3 = t$2 === 1;
}
console.log(t$3.toString());
let t$5 = t;
if (t$5) {
  /** @type {number} */
  let t$4;
  try {
    t$4 = failing(1);
  } catch (e$) {
    if (!(e$ instanceof TooBig)) throw e$;
    t$4 = 2;
  }
  t$5 = t$4 === 1;
}
console.log(t$5.toString());
console.log((t || noted(7) === 0).toString());
/** @type {number} */
let t$6;
if (t) {
  try {
    t$6 = failing(4);
  } catch (e$) {
    if (!(e$ instanceof TooBig)) throw e$;
    t$6 = 4;
  }
} else {
  t$6 = 0;
}
console.log(t$6.toString());
let i = 0;
while (true) {
  /** @type {number} */
  let t$7;
  try {
    t$7 = failing(i);
  } catch (e$) {
    if (!(e$ instanceof TooBig)) throw e$;
    t$7 = 100;
  }
  if (t$7 >= 3) {
    break;
  }
  i = (i + 1) | 0;
}
console.log(i.toString());
console.log(`${sameFloat(null, null)} ${sameFloat(0.5, null)} ${sameFloat(0.5, 0.5)}`);
console.log(calls.toString());
