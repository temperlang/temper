import { failureBase } from "@temperlang/core";

function yay() {
  console.log("yay");
}

function meh() {
  console.log("meh");
}

function boo() {
  console.log("boo");
}

/**
 * @param {number} x
 * @returns {number}
 * @throws {Oops}
 */
function f(x) {
  return x;
}

/**
 * @param {number} x
 * @returns {number}
 * @throws {Oops}
 */
function g(x) {
  return x;
}

/**
 * @param {number} x
 * @returns {number}
 * @throws {Oops}
 */
function i(x) {
  return x;
}

class Problem extends Error {}

failureBase(Problem);

class Oops extends Problem {
  /** @returns {string} */
  get message() {
    return "oops";
  }
}

console.log("Checkpoint 0");
try {
  /** @type {number} */
  let a;
  a = "1";
  throw new Oops();
} catch (e$) {
  if (!(e$ instanceof Oops)) throw e$;
  yay();
}
console.log("Checkpoint 1");
/** @type {void} */
let t$0;
{
  const a2 = 1;
  yay();
  t$0 = undefined;
}
t$0;
console.log("Checkpoint 2");
{
  let a3 = 1;
  try {
    a3 = "2";
    throw new Oops();
  } catch (e$) {
    if (!(e$ instanceof Oops)) throw e$;
    meh();
  }
  if (a3 === 1) {
    meh();
  } else {
    meh();
  }
}
console.log("Checkpoint 3");
/** @type {void} */
let t$1;
{
  let a4 = 1;
  a4 = 2;
  t$1 = yay();
}
t$1;
console.log("Checkpoint 4");
try {
  f(0);
  yay();
} catch (e$) {
  if (!(e$ instanceof Oops)) throw e$;
  boo();
}
try {
  f("0");
  meh();
} catch (e$) {
  if (!(e$ instanceof Oops)) throw e$;
  meh();
}
console.log("Checkpoint 5");
try {
  g(0);
  yay();
} catch (e$) {
  if (!(e$ instanceof Oops)) throw e$;
  boo();
}
try {
  g("0");
  meh();
} catch (e$) {
  if (!(e$ instanceof Oops)) throw e$;
  meh();
}
console.log("Checkpoint 6");
try {
  i(0);
  yay();
} catch (e$) {
  if (!(e$ instanceof Oops)) throw e$;
  boo();
}
try {
  i("0");
  meh();
} catch (e$) {
  if (!(e$ instanceof Oops)) throw e$;
  meh();
}
