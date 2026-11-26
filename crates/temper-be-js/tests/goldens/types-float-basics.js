import {
  ParseError,
  clampInt64,
  cmpFloat64,
  eqFloat64,
  float64ToInt32,
  float64ToInt32Unsafe,
  float64ToInt64,
  float64ToInt64Unsafe,
  float64ToString,
  int64ToFloat64,
  int64ToFloat64Unsafe,
  stringToFloat64,
} from "@temperlang/core";

/**
 * @param {number} a
 * @param {number} b
 * @returns {boolean}
 */
function gt(a, b) {
  return cmpFloat64(a, b) > 0;
}

/**
 * @param {number} a
 * @param {number} b
 * @returns {boolean}
 */
function lt(a, b) {
  return cmpFloat64(a, b) < 0;
}

/**
 * @param {number} a
 * @param {number} b
 * @returns {boolean}
 */
function eq(a, b) {
  return eqFloat64(a, b);
}

/**
 * @param {number} a
 * @param {number} b
 * @returns {boolean}
 */
function neq(a, b) {
  return !eqFloat64(a, b);
}

/**
 * @param {number} a
 * @param {number} b
 * @returns {number}
 */
function add(a, b) {
  return a + b;
}

/** @param {string} string */
function checkParse(string) {
  /** @type {string} */
  let converted;
  try {
    converted = float64ToString(stringToFloat64(string));
  } catch (e$) {
    if (!(e$ instanceof ParseError)) throw e$;
    converted = "failed";
  }
  console.log(`Parse ${string} -> ${converted}`);
}

/** @param {string} string */
function checkTiny(string) {
  /** @type {string} */
  let positive;
  try {
    positive = (cmpFloat64(stringToFloat64(string), 0.0) > 0).toString();
  } catch (e$) {
    if (!(e$ instanceof ParseError)) throw e$;
    positive = "failed";
  }
  console.log(`Parse ${string} > 0 -> ${positive}`);
}

const negativeZero = -0.0;
let zero = 0.0;
zero = zero;
let one = 1.0;
one = one;
let two = 2.0;
two = two;
let three = 3.0;
three = three;
if (gt(one, zero)) {
  console.log("greater than");
}
if (lt(zero, one)) {
  console.log("less than");
}
if (eq(zero, one)) {
  console.log("equality is wrong");
}
if (neq(zero, one)) {
  console.log("inequality");
}
console.log(float64ToString(zero));
if (neq(negativeZero, zero)) {
  console.log("zeroes not equal");
}
if (lt(negativeZero, zero)) {
  console.log("negZero < zero");
}
if (eqFloat64(zero - zero, zero)) {
  console.log("zero subtraction");
}
if (eqFloat64(zero + zero, zero)) {
  console.log("zero addition");
}
if (eqFloat64(negativeZero - zero, negativeZero)) {
  console.log("negative zero minus zero is negative");
}
if (eqFloat64(negativeZero + zero, zero)) {
  console.log("negative zero plus zero is positive");
}
if (eqFloat64(zero - negativeZero, zero)) {
  console.log("zero minus negative zero is zero");
}
if (eqFloat64(zero + negativeZero, zero)) {
  console.log("zero plus negative zero is zero");
}
if (eqFloat64(negativeZero - negativeZero, zero)) {
  console.log("negative zero minus negative zero is zero");
}
if (eqFloat64(negativeZero + negativeZero, negativeZero)) {
  console.log("negative zero plus negative zero is negative zero");
}
console.log(float64ToString(negativeZero));
let third = one / three;
third = third;
let half = one / two;
half = half;
let twoThirds = two / three;
twoThirds = twoThirds;
if (gt(third, half)) {
  console.log("oops third > half");
}
if (gt(half, twoThirds)) {
  console.log("oops half > twoThirds");
}
if (neq(third * two, twoThirds)) {
  console.log("misaligned multiplication");
}
if (neq(add(third, twoThirds), one)) {
  console.log("doesn't add back up");
}
console.log(float64ToString(half));
console.log(float64ToString(4.0 ** -half));
console.log(float64ToString(third));
console.log(float64ToString(twoThirds));
console.log(float64ToString(one));
const almostBig = 1e25 * one;
console.log(float64ToString(almostBig));
let big = 99999999999999.0 * 999999999999.0;
big = big;
console.log(float64ToString(big));
let bigger = big * big * big * big * big * big * big * big;
bigger = bigger;
console.log(float64ToString(bigger));
const middling = -1500.0 * one;
console.log(`${float64ToString(middling)} ${float64ToString(1000000.0 * one)} ${float64ToString(9999999.0 * one)} ${float64ToString(0.001 * one)}`);
console.log(`${float64ToString(1e21 * one)} ${float64ToString(1.5e-10 * one)} ${float64ToString(2e23 * one)}`);
let biggest = bigger * bigger * bigger * bigger * bigger * bigger;
biggest = biggest;
console.log(`biggest ${float64ToString(biggest)}`);
let tiniest = one / biggest;
tiniest = tiniest;
console.log(`tiniest ${float64ToString(tiniest)}`);
let negativeInfinity = biggest * -1.0;
negativeInfinity = negativeInfinity;
console.log(`negative infinity ${float64ToString(negativeInfinity)}`);
let infinity = Infinity;
infinity = infinity;
console.log(float64ToString(infinity));
console.log("processed an infinity");
let nan = NaN;
nan = nan;
console.log(float64ToString(nan));
if (cmpFloat64(nan, infinity) > 0) {
  console.log("NaN is biggest");
} else {
  console.log("Infinity is biggest");
}
console.log(`NaN == NaN? ${eqFloat64(nan, NaN)}`);
console.log(`Int to float: ${float64ToString(float64ToInt32Unsafe(two))}`);
console.log(`Positive to int: ${float64ToInt32Unsafe(4.9 * one)}`);
console.log(`Negative to int: ${float64ToInt32Unsafe(-4.9 * one)}`);
/** @type {string} */
let t$0;
try {
  t$0 = float64ToInt32(infinity).toString();
} catch (e$) {
  if (!(e$ instanceof ParseError)) throw e$;
  t$0 = "no";
}
console.log(`Infinity to int: ${t$0}`);
/** @type {string} */
let t$1;
try {
  t$1 = float64ToInt32(NaN).toString();
} catch (e$) {
  if (!(e$ instanceof ParseError)) throw e$;
  t$1 = "no";
}
console.log(`NaN to int: ${t$1}`);
const oneInt64 = float64ToInt64Unsafe(one);
const notSoBig = clampInt64(2147483647n * oneInt64);
const notSoSmall = clampInt64(-2147483648n * oneInt64);
const slightlyBigger = float64ToInt64Unsafe(int64ToFloat64Unsafe(notSoBig) + 1.5);
const slightlySmaller = float64ToInt64Unsafe(int64ToFloat64Unsafe(notSoSmall) - 1.5);
const tooBigForFloat64 = clampInt64(9007199254740992n * oneInt64);
const okToConvert64 = int64ToFloat64Unsafe(clampInt64(tooBigForFloat64 - 1n));
const sneakyBig = okToConvert64 + 1.0;
console.log(`Bigger to Int64: ${slightlyBigger.toString()}`);
console.log(`Smaller to Int64: ${slightlySmaller.toString()}`);
console.log(`Barely ok to Int64: ${float64ToInt64Unsafe(okToConvert64).toString()}`);
/** @type {string} */
let t$2;
try {
  t$2 = float64ToInt64(sneakyBig).toString();
} catch (e$) {
  if (!(e$ instanceof ParseError)) throw e$;
  t$2 = "no";
}
console.log(`Sneaky big to Int64: ${t$2}`);
/** @type {string} */
let t$3;
try {
  t$3 = float64ToString(int64ToFloat64(tooBigForFloat64));
} catch (e$) {
  if (!(e$ instanceof ParseError)) throw e$;
  t$3 = "no";
}
console.log(`Too big Int64 to Float64: ${t$3}`);
/** @type {string} */
let t$4;
try {
  t$4 = float64ToInt64(infinity).toString();
} catch (e$) {
  if (!(e$ instanceof ParseError)) throw e$;
  t$4 = "no";
}
console.log(`Infinity to Int64: ${t$4}`);
/** @type {string} */
let t$5;
try {
  t$5 = float64ToInt64(NaN).toString();
} catch (e$) {
  if (!(e$ instanceof ParseError)) throw e$;
  t$5 = "no";
}
console.log(`NaN to Int64: ${t$5}`);
checkParse("-1.5");
checkParse("-0");
checkParse("2.0");
checkParse("2");
checkParse(" 2 ");
checkParse("5e-1");
checkParse("NaN");
checkParse("-Infinity");
checkTiny("2.225e-308");
checkTiny("5e-324");
checkParse("1e-400");
checkParse("-1e-400");
checkParse("2.");
checkParse(".2");
checkParse("2.0.0");
checkParse("-inf");
checkParse("totally");
