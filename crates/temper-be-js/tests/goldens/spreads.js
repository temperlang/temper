import {
  PromiseBuilder,
  adaptAwaiter,
  bubble,
  panic,
  runAsync,
  type,
} from "@temperlang/core";

/**
 * @param {Box} b
 * @returns {Box}
 */
function grown(b) {
  {
    const spread = b;
    return new Box((b.w + 1) | 0, spread.h, spread.label);
  }
}

/**
 * @param {Sized} s
 * @returns {number}
 */
function areaOf(s) {
  return s.area();
}

/**
 * @param {string} s
 * @param {number} n
 * @returns {number}
 */
function said(s, n) {
  console.log(s);
  return n;
}

/**
 * @param {number} x
 * @param {Box} b
 * @returns {string}
 */
function both(x, b) {
  return `${x} ${b.toString()}`;
}

/**
 * @param {Box} b
 * @param {number} n
 * @returns {number}
 */
function widths(b, n) {
  let t = b;
  {
    let i = 0;
    while (i < n) {
      /** @type {Box} */
      let t$0;
      {
        const spread = t;
        t$0 = new Box(t.h, (t.w + 1) | 0, spread.label);
      }
      t = t$0;
      i = (i + 1) | 0;
    }
  }
  while (true) {
    /** @type {Box} */
    let t$1;
    {
      const spread2 = t;
      t$1 = new Box(spread2.w, 1, spread2.label);
    }
    if (t$1.area() >= 9) {
      break;
    }
    /** @type {Box} */
    let t$2;
    {
      const spread = t;
      t$2 = new Box((t.w + 1) | 0, spread.h, spread.label);
    }
    t = t$2;
  }
  {
    let i = 0;
    while (i < 2) {
      /** @type {Box} */
      let t$3;
      {
        const spread = t;
        const arg = said("h", t.w);
        const arg2 = said("w", t.h);
        const arg3 = spread.label;
        t$3 = new Box(arg2, arg, arg3);
      }
      t = t$3;
      i = (i + 1) | 0;
    }
  }
  if (t.w > 0) {
    /** @type {Box} */
    let t$4;
    {
      const spread = b;
      t$4 = new Box(spread.w, spread.h, "inner");
    }
    const b2 = t$4;
    console.log(b2.toString());
  }
  return t.w;
}

/**
 * @template T
 * @param {Two<T>} t
 * @returns {Two<T>}
 */
function swapped(t) {
  {
    /** @type {Two<T>} */
    const spread = t;
    return new Two(t.second, t.first);
  }
}

/**
 * @param {Shape} x
 * @returns {Shape}
 */
function doubled(x) {
  /** @type {Shape} */
  let t$8;
  if (x instanceof Circle) {
    /** @type {Shape} */
    let t$5;
    {
      const spread = /** @type {Circle} */ (/** @type {unknown} */ (x));
      t$5 = new Circle(Math.imul(/** @type {Circle} */ (/** @type {unknown} */ (x)).r, 2));
    }
    t$8 = t$5;
  } else {
    /** @type {Square} */
    let t$7;
    if (x instanceof Square) {
      /** @type {Square} */
      let t$6;
      {
        const spread2 = /** @type {Square} */ (/** @type {unknown} */ (x));
        t$6 = new Square(Math.imul(/** @type {Square} */ (/** @type {unknown} */ (x)).side, 2));
      }
      t$7 = t$6;
    } else {
      t$7 = panic();
    }
    t$8 = t$7;
  }
  const made = t$8;
  return made;
}

/**
 * @param {Shape} x
 * @returns {number}
 */
function size(x) {
  const n = x instanceof Circle ? /** @type {Circle} */ (/** @type {unknown} */ (x)).r : x instanceof Square ? /** @type {Square} */ (/** @type {unknown} */ (x)).side : panic();
  return n;
}

/**
 * @param {boolean} c
 * @returns {Box}
 */
function picked(c) {
  if (c) {
    {
      const spread = a;
      return new Box(9, spread.h, spread.label);
    }
  } else {
    return a;
  }
}

/**
 * @param {(arg0: number) => Box} f
 * @returns {Box}
 */
function applied(f) {
  return f(4);
}

/**
 * @param {number} n
 * @returns {number}
 */
function positive(n) {
  if (n < 0) {
    bubble();
  }
  return n;
}

/**
 * @param {Box} b
 * @param {number} n
 * @returns {Box}
 */
function sized(b, n) {
  {
    const spread = b;
    return new Box(positive(n), spread.h, spread.label);
  }
}

class Sized extends type() {
  /** @type {() => number} */
  area = /** @type {any} */ (undefined);
}

class Box extends type(Sized) {
  /** @type {number} */
  #w;
  /** @type {number} */
  #h;
  /** @type {string | null} */
  #label;

  /**
   * @param {{ w: number, h: number, label?: string | null }} props
   * @returns {Box}
   */
  static new(props) {
    return new Box(props.w, props.h, props.label);
  }

  /**
   * @param {number} w
   * @param {number} h
   * @param {string | null} [label]
   */
  constructor(w, h, label) {
    super();
    label ??= null;
    this.#w = w;
    this.#h = h;
    this.#label = label;
  }

  /** @returns {number} */
  get w() {
    return this.#w;
  }

  /** @returns {number} */
  get h() {
    return this.#h;
  }

  /** @returns {string | null} */
  get label() {
    return this.#label;
  }

  /** @returns {number} */
  area() {
    return Math.imul(this.#w, this.#h);
  }

  /** @returns {string} */
  toString() {
    return `${this.#label ?? "box"} ${this.#w}x${this.#h}`;
  }
}

/** @template T */
class Two extends type() {
  /** @type {T} */
  #first;
  /** @type {T} */
  #second;

  /**
   * @template T
   * @param {{ first: T, second: T }} props
   * @returns {Two<T>}
   */
  static new(props) {
    return new Two(props.first, props.second);
  }

  /**
   * @param {T} first
   * @param {T} second
   */
  constructor(first, second) {
    super();
    this.#first = first;
    this.#second = second;
  }

  /** @returns {T} */
  get first() {
    return this.#first;
  }

  /** @returns {T} */
  get second() {
    return this.#second;
  }
}

class Step extends type() {
  /** @type {number} */
  #n;
  /** @type {string} */
  #tag;

  /**
   * @param {{ n: number, tag: string }} props
   * @returns {Step}
   */
  static new(props) {
    return new Step(props.n, props.tag);
  }

  /**
   * @param {number} n
   * @param {string} tag
   */
  constructor(n, tag) {
    super();
    this.#n = n;
    this.#tag = tag;
  }

  /** @returns {number} */
  get n() {
    return this.#n;
  }

  /** @returns {string} */
  get tag() {
    return this.#tag;
  }

  /** @returns {Step} */
  next() {
    {
      const spread = this;
      return new Step((this.#n + 1) | 0, spread.#tag);
    }
  }
}

class Shape extends type() {}

class Circle extends type(Shape) {
  /** @type {number} */
  #r;

  /** @param {number} r */
  constructor(r) {
    super();
    this.#r = r;
  }

  /** @returns {number} */
  get r() {
    return this.#r;
  }
}

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

class Dial extends type() {
  /** @type {number} */
  #level;
  /** @type {string | null} */
  #note;
  /** @type {number} */
  #twice;

  /**
   * @param {{ level?: number | null, note?: string | null, twice?: number | null }} props
   * @returns {Dial}
   */
  static new(props) {
    return new Dial(props.level, props.note, props.twice);
  }

  /**
   * @param {number | null} [level]
   * @param {string | null} [note]
   * @param {number | null} [twice]
   */
  constructor(level, note, twice) {
    super();
    level ??= 5;
    note ??= "n";
    twice ??= Math.imul(level, 2);
    this.#level = level;
    this.#note = note;
    this.#twice = twice;
  }

  /** @returns {number} */
  get level() {
    return this.#level;
  }

  /** @param {number} value */
  set level(value) {
    this.#level = value;
  }

  /** @returns {string | null} */
  get note() {
    return this.#note;
  }

  /** @returns {number} */
  get twice() {
    return this.#twice;
  }
}

class Mark extends type() {
  /** @type {string | null} */
  #note;
  /** @type {number} */
  #size;

  /**
   * @param {{ note?: string | null, size?: number | null }} props
   * @returns {Mark}
   */
  static new(props) {
    return new Mark(props.note, props.size);
  }

  /**
   * @param {string | null} [note]
   * @param {number | null} [size]
   */
  constructor(note, size) {
    super();
    note ??= "n";
    size ??= 2;
    this.#note = note;
    this.#size = size;
  }

  /** @returns {string | null} */
  get note() {
    return this.#note;
  }

  /** @returns {number} */
  get size() {
    return this.#size;
  }
}

const a = new Box(2, 3);
const b = grown(a);
console.log(`${a.toString()} ${b.toString()} ${areaOf(b)}`);
/** @type {Box} */
let t$9;
{
  const spread = b;
  t$9 = new Box(spread.w, spread.h, "named");
}
console.log(t$9.toString());
let t$11 = a.w > 1;
if (t$11) {
  /** @type {Box} */
  let t$10;
  {
    const spread2 = a;
    t$10 = new Box(10, spread2.h, spread2.label);
  }
  t$11 = t$10.area() > 20;
}
const wide = t$11;
/** @type {Two<number>} */
const p = new Two(1, 2);
/** @type {Two<number>} */
let t$12;
{
  /** @type {Two<number>} */
  const spread3 = p;
  t$12 = new Two(spread3.first, 5);
}
/** @type {Two<number>} */
const q = t$12;
console.log(`${wide} ${q.first} ${q.second}`);
/** @type {Box} */
let t$13;
{
  /** @type {Box} */
  let t$14;
  {
    const spread5 = a;
    t$14 = new Box(7, spread5.h, spread5.label);
  }
  const spread4 = t$14;
  t$13 = new Box(spread4.w, 8, spread4.label);
}
const nested = t$13;
const t$16 = said("one", 1);
/** @type {Box} */
let t$15;
{
  const spread6 = a;
  t$15 = new Box(spread6.w, said("two", 2), spread6.label);
}
console.log(both(t$16, t$15));
let t$18 = a.w > 5;
if (!t$18) {
  /** @type {Box} */
  let t$17;
  {
    const spread7 = a;
    t$17 = new Box(said("three", 3), spread7.h, spread7.label);
  }
  t$18 = t$17.w === 3;
}
console.log(`${nested.toString()} ${t$18}`);
console.log(widths(a, 3).toString());
const s = new Step(1, "s").next().next();
/** @type {Two<number>} */
let t$19;
{
  /** @type {Two<number>} */
  const spread8 = q;
  t$19 = new Two(spread8.first, spread8.second);
}
/** @type {Two<number>} */
const same = t$19;
/** @type {Two<string>} */
const words = new Two("a", "b");
/** @type {Two<string>} */
const flipped = swapped(words);
console.log(`${s.n} ${s.tag} ${same.first}${same.second} ${flipped.first}${flipped.second}`);
console.log(`${size(doubled(new Circle(3)))} ${size(doubled(new Square(5)))}`);

/**
 * @param {number} n
 * @returns {Box}
 */
function fn(n) {
  {
    const spread = a;
    return new Box(spread.w, n, spread.label);
  }
}

const made = applied(fn);
console.log(`${picked(true).toString()} ${picked(false).toString()} ${made.toString()}`);
const dial = new Dial(1, null);
dial.level = 4;
/** @type {Dial} */
let t$20;
{
  const spread9 = dial;
  t$20 = new Dial(spread9.level, "m", spread9.twice);
}
const turned = t$20;
const mark = new Mark(null);
/** @type {Mark} */
let t$21;
{
  const spread10 = mark;
  t$21 = new Mark(spread10.note, 3);
}
const bigger = t$21;
console.log(`${turned.level} ${turned.note ?? "-"} ${turned.twice} ${bigger.note ?? "-"} ${bigger.size}`);
/** @type {Box} */
let t$22;
{
  const spread11 = a;
  /** @type {number} */
  let t$23;
  try {
    t$23 = positive(-1);
  } catch {
    t$23 = 0;
  }
  const t$25 = t$23;
  /** @type {number} */
  let t$24;
  try {
    t$24 = positive(5);
  } catch {
    t$24 = -1;
  }
  t$22 = new Box(t$25, t$24, spread11.label);
}
const caught = t$22;
/** @type {string} */
let t$26;
try {
  t$26 = sized(a, 4).toString();
} catch {
  t$26 = "-";
}
console.log(`${caught.toString()} ${t$26}`);
/** @type {string} */
let t$27;
try {
  sized(a, -1);
  t$27 = "sized";
} catch {
  t$27 = "bubbled";
}
console.log(t$27);
/** @type {import("@temperlang/core/async.js").PromiseBuilder<number>} */
const gate = new PromiseBuilder();

/**
 * @param {(p: Promise<unknown>) => void} doAwait
 * @returns {Generator<any>}
 */
function* fn2(doAwait) {
  /** @type {Box} */
  let t$28;
  {
    const spread = a;
    const arg = spread.w;
    /** @type {number} */
    let arg2;
    try {
      arg2 = yield doAwait(gate.promise);
    } catch {
      arg2 = -1;
    }
    t$28 = new Box(arg, arg2, "later");
  }
  const later = t$28;
  console.log(later.toString());
}

runAsync(adaptAwaiter(fn2));
gate.complete(6);
