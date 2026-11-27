import {
  InvalidCodePoint,
  MissingKey,
  ParseError,
  dateConstructor,
  dateFromIsoString,
  eqFloat64,
  failureBase,
  float64ToInt32,
  int64ToInt32,
  mapBuilderRemove,
  mappedGet,
  panic,
  stringBuilderAppendCodePoint,
  stringFromCodePoint,
  stringFromCodePoints,
  stringToFloat64,
  stringToInt32,
  stringToInt64,
  type,
} from "@temperlang/core";
import {
  JsonError,
  JsonSyntaxError,
  JsonTextProducer,
  parseJson,
} from "@temperlang/std/json";
import {
  CodePart,
  CodeRange,
  CodeSet,
  Group,
  RegexNode,
  Repeat,
} from "@temperlang/std/regex";
import {
  InvalidDate,
  InvalidIsoDate,
  TemporalError,
} from "@temperlang/std/temporal";

/**
 * @param {number} n
 * @returns {number}
 * @throws {Positive}
 */
function failing(n) {
  if (n > 0) {
    throw new Positive(n);
  }
  return n;
}

/**
 * @param {Shape} s
 * @returns {Square}
 * @throws {NotSquare}
 */
function downcast(s) {
  if (s instanceof Square) {
    return /** @type {Square} */ (/** @type {unknown} */ (s));
  } else {
    throw new NotSquare();
  }
}

/**
 * @param {() => number} f
 * @returns {number}
 */
function calling(f) {
  try {
    return f();
  } catch (e$) {
    if (!(e$ instanceof Problem)) throw e$;
    return -9;
  }
}

/**
 * @param {number} codePoint
 * @returns {string}
 * @throws {InvalidCodePoint}
 */
function appending(codePoint) {
  const sb = [""];
  stringBuilderAppendCodePoint(sb, codePoint);
  return sb[0];
}

/**
 * @param {string} text
 * @returns {string}
 * @throws {JsonError}
 */
function parsing(text) {
  parseJson(text);
  return "parsed";
}

/**
 * @param {number} n
 * @returns {number}
 * @throws {Positive}
 */
function explode(n) {
  if (n > 0) {
    panic();
  }
  throw new Positive(n);
}

/**
 * @param {string} name
 * @returns {number}
 */
function noted(name) {
  console.log(`${name} fallback`);
  return -2;
}

/**
 * @returns {number}
 * @throws {Positive}
 */
function inner() {
  try {
    return explode(1);
  } catch (e$) {
    if (!(e$ instanceof Positive)) throw e$;
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

class Problem extends Error {}

failureBase(Problem);

class Positive extends Problem {
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
    return `positive (n: ${this.#n})`;
  }
}

class NotSquare extends Problem {
  /** @returns {string} */
  get message() {
    return "not square";
  }
}

/** @type {number} */
let zero;
try {
  zero = failing(0);
} catch (e$) {
  if (!(e$ instanceof Positive)) throw e$;
  zero = -1;
}
/** @type {number} */
let a;
try {
  a = failing(1);
} catch (e$) {
  if (!(e$ instanceof Positive)) throw e$;
  a = -1;
}
/** @type {number} */
let b;
try {
  b = downcast(new Circle()).side;
} catch (e$) {
  if (!(e$ instanceof NotSquare)) throw e$;
  b = -2;
}
/** @type {number} */
let e;
try {
  e = stringToInt32("nope", null);
} catch (e$) {
  if (!(e$ instanceof ParseError)) throw e$;
  e = -5;
}
/** @type {number} */
let f;
try {
  f = stringToFloat64("x");
} catch (e$) {
  if (!(e$ instanceof ParseError)) throw e$;
  f = -6.0;
}
/** @type {string} */
let g;
try {
  g = stringFromCodePoint(1114112);
} catch (e$) {
  if (!(e$ instanceof InvalidCodePoint)) throw e$;
  g = "?";
}
const digits = /** @type {RegexNode} */ (/** @type {unknown} */ (new Repeat(/** @type {RegexNode} */ (/** @type {unknown} */ (new CodeSet(Object.freeze([/** @type {CodePart} */ (/** @type {unknown} */ (new CodeRange(48, 57)))]), false))), 1, null, false))).compiled();
/** @type {string | null} */
let t$0;
{
  /** @type {Group | null} */
  let t$1;
  {
    const subject3 = digits.find("none");
    t$1 = subject3 == null ? null : subject3.full;
  }
  const subject2 = t$1;
  t$0 = subject2 == null ? null : subject2.value;
}
const h = t$0 ?? "no digits";
const i = calling(() => failing(2));
console.log(`${a} ${b} ${e} ${eqFloat64(f, -6.0)} ${g} ${h} ${i}`);
/** @type {bigint} */
let k;
try {
  k = stringToInt64("zz", null);
} catch (e$) {
  if (!(e$ instanceof ParseError)) throw e$;
  k = -7n;
}
/** @type {number} */
let l;
try {
  l = stringToInt32("99999999999", null);
} catch (e$) {
  if (!(e$ instanceof ParseError)) throw e$;
  l = -8;
}
/** @type {number} */
let m;
try {
  m = float64ToInt32(1e20);
} catch (e$) {
  if (!(e$ instanceof ParseError)) throw e$;
  m = -9;
}
/** @type {number} */
let n;
try {
  n = int64ToInt32(5000000000n);
} catch (e$) {
  if (!(e$ instanceof ParseError)) throw e$;
  n = -10;
}
/** @type {readonly number[]} */
const points = Object.freeze([65, 1114112]);
/** @type {string} */
let o;
try {
  o = stringFromCodePoints(/** @type {number[]} */ (points));
} catch (e2) {
  if (!(e2 instanceof InvalidCodePoint)) throw e2;
  o = e2.codePoint.toString(16);
}
/** @type {string} */
let q;
try {
  q = appending(-1);
} catch (e3) {
  if (!(e3 instanceof InvalidCodePoint)) throw e3;
  q = e3.codePoint.toString();
}
/** @type {Map<string, number>} */
const builder = new Map();
/** @type {number} */
let r;
try {
  r = mappedGet(builder, "x");
} catch (e$) {
  if (!(e$ instanceof MissingKey)) throw e$;
  r = -11;
}
/** @type {string} */
let r2;
try {
  r2 = mapBuilderRemove(builder, "y").toString();
} catch (e4) {
  if (!(e4 instanceof MissingKey)) throw e4;
  r2 = /** @type {Error} */ (/** @type {unknown} */ (e4)).message;
}
/** @type {string} */
let s;
try {
  s = dateConstructor(2023, 2, 30, InvalidDate).toISOString().split("T")[0];
} catch (e$) {
  if (!(e$ instanceof TemporalError)) throw e$;
  s = "no date";
}
/** @type {string} */
let t;
try {
  t = dateFromIsoString("2023-13-01", InvalidIsoDate).toISOString().split("T")[0];
} catch (e$) {
  if (!(e$ instanceof TemporalError)) throw e$;
  t = "bad date";
}
/** @type {string} */
let u;
try {
  u = parsing("[1,");
} catch (e$) {
  if (!(e$ instanceof JsonError)) throw e$;
  u = "bad json";
}
const partial = new JsonTextProducer();
partial.startObject();
/** @type {string} */
let v;
try {
  v = partial.toJsonString();
} catch (e5) {
  if (!(e5 instanceof JsonSyntaxError)) throw e5;
  v = e5.problem;
}
console.log(`${k.toString()} ${l} ${m} ${n} ${o} ${q} ${r} ${r2} ${s} ${t} ${u}`);
console.log(v);
console.log("panicking");
/** @type {number} */
let p;
try {
  p = inner();
} catch (e$) {
  if (!(e$ instanceof Positive)) throw e$;
  p = -1;
}
console.log(`after the panic ${p}`);
