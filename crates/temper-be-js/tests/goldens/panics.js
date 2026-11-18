import {
  bubble,
  divInt64,
  divIntInt,
  eqFloat64,
  int64ToInt32,
  isBubble,
  panic,
  requireInstanceOf,
  stringFromCodePoint,
  stringToFloat64,
  stringToInt32,
  type,
} from "@temperlang/core";
import {
  CodePart,
  CodeRange,
  CodeSet,
  RegexNode,
  Repeat,
} from "@temperlang/std/regex";

/**
 * @param {number} n
 * @returns {number}
 */
function failing(n) {
  if (n > 0) {
    bubble();
  }
  return n;
}

/**
 * @param {Shape} s
 * @returns {Square}
 */
function downcast(s) {
  return requireInstanceOf(s, Square);
}

/**
 * @param {() => number} f
 * @returns {number}
 */
function calling(f) {
  try {
    return f();
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    return -9;
  }
}

/**
 * @param {number} n
 * @returns {number}
 */
function explode(n) {
  if (n > 0) {
    panic();
  }
  bubble();
}

/**
 * @param {string} name
 * @returns {number}
 */
function noted(name) {
  console.log(`${name} fallback`);
  return -2;
}

/** @returns {number} */
function inner() {
  try {
    return explode(1);
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    return noted("inner");
  }
}

class Shape extends type() {}

class Square extends type(Shape) {
  /** @type {number} */
  #side;

  /** @param {number} side */
  constructor(side) {
    super();
    this.#side = side;
  }

  /** @returns {number} */
  get side() {
    return this.#side;
  }
}

class Circle extends type(Shape) {}

/** @type {number} */
let zero;
try {
  zero = failing(0);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  zero = -1;
}
/** @type {number} */
let a;
try {
  a = failing(1);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  a = -1;
}
/** @type {number} */
let b;
try {
  b = downcast(new Circle()).side;
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  b = -2;
}
/** @type {number} */
let c;
try {
  c = divIntInt(1, zero);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  c = -3;
}
/** @type {number} */
let d;
try {
  d = int64ToInt32(divInt64(1n, BigInt(zero)));
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  d = -4;
}
/** @type {number} */
let e;
try {
  e = stringToInt32("nope", null);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  e = -5;
}
/** @type {number} */
let f;
try {
  f = stringToFloat64("x");
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  f = -6.0;
}
/** @type {string} */
let g;
try {
  g = stringFromCodePoint(1114112);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  g = "?";
}
const digits = /** @type {RegexNode} */ (/** @type {unknown} */ (new Repeat(/** @type {RegexNode} */ (/** @type {unknown} */ (new CodeSet(Object.freeze([/** @type {CodePart} */ (/** @type {unknown} */ (new CodeRange(48, 57)))]), false))), 1, null, false))).compiled();
/** @type {string} */
let h;
try {
  h = digits.find("none").full.value;
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  h = "no digits";
}
const i = calling(() => failing(2));
console.log(`${a} ${b} ${c} ${d} ${e} ${eqFloat64(f, -6.0)} ${g} ${h} ${i}`);
console.log("panicking");
/** @type {number} */
let p;
try {
  p = inner();
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  p = -1;
}
console.log(`after the panic ${p}`);
