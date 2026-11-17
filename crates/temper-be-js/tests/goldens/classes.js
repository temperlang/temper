import {
  cmpInt32,
  listedGet,
  listedJoin,
  requireInstanceOf,
  type,
} from "@temperlang/core";

/**
 * @param {number} n
 * @returns {Loose | null}
 */
function maybeLoose(n) {
  if (n === 0) {
    return null;
  } else {
    return new Loose(n);
  }
}

/**
 * @param {Shape} x
 * @returns {Shape}
 */
function shape(x) {
  return x;
}

/**
 * @param {(arg0: Loose | null, arg1: Loose | null) => boolean} eq
 * @returns {string}
 */
function compared(eq) {
  /** @type {readonly number[]} */
  const ns = Object.freeze([0, 1, -1, 2]);
  /** @type {string[]} */
  const out = [];
  {
    const length = ns.length;
    let i = 0;
    while (i < length) {
      const a = listedGet(/** @type {number[]} */ (ns), i);
      i = (i + 1) | 0;
      {
        const length2 = ns.length;
        let i2 = 0;
        while (i2 < length2) {
          const b = listedGet(/** @type {number[]} */ (ns), i2);
          i2 = (i2 + 1) | 0;
          out.push(eq(maybeLoose(a), maybeLoose(b)) ? "T" : "f");
        }
      }
    }
  }
  return listedJoin(out, "", (s) => s);
}

/**
 * @param {number} n
 * @returns {string}
 */
function typeNamed(n) {
  {
    if (n === 0) {
      return "undefined";
    } else if (n === 1) {
      return "boolean";
    } else if (n === 2) {
      return "number";
    } else if (n === 3) {
      return "bigint";
    } else if (n === 4) {
      return "number";
    } else {
      return "string";
    }
  }
}

/**
 * @param {string} t
 * @returns {string}
 */
function same(t) {
  return t;
}

export class Shape extends type() {
  /** @type {number} */
  area = /** @type {any} */ (undefined);

  /** @returns {string} */
  describe() {
    return `shape of area ${this.area}`;
  }
}

/** @type {Rect} */
let Rect$unit;

export class Rect extends type(Shape) {
  /** @type {number} */
  #w;
  /** @type {number} */
  #h;

  /** @returns {Rect} */
  static get unit() {
    return Rect$unit;
  }

  /**
   * @param {{ w: number, h: number }} props
   * @returns {Rect}
   */
  static new(props) {
    return new Rect(props.w, props.h);
  }

  /**
   * @param {number} w
   * @param {number} h
   */
  constructor(w, h) {
    super();
    this.#w = w;
    this.#h = h;
  }

  /** @returns {number} */
  get w() {
    return this.#w;
  }

  /** @returns {number} */
  get h() {
    return this.#h;
  }

  /** @returns {number} */
  get area() {
    return Math.imul(this.#w, this.#h);
  }

  /** @returns {string} */
  toString() {
    return `Rect(${this.#w}, ${this.#h})`;
  }

  /**
   * @param {number} k
   * @returns {Rect}
   */
  scaled(k) {
    return new Rect(Math.imul(this.#w, k), Math.imul(this.#h, k));
  }
}

export class Square extends type(Shape) {
  /** @type {number} */
  #side;

  /** @param {number} side */
  constructor(side) {
    super();
    this.#side = side;
  }

  /** @returns {number} */
  get area() {
    return Math.imul(this.#side, this.#side);
  }

  /** @returns {string} */
  describe() {
    return `square of side ${this.#side}`;
  }
}

class Counter extends type() {
  /** @type {number} */
  #count;
  /** @type {number} */
  #hidden;

  constructor() {
    super();
    this.#count = 0;
    this.#hidden = 0;
  }

  /** @returns {number} */
  get count() {
    return this.#count;
  }

  /** @param {number} value */
  set count(value) {
    this.#count = value;
  }

  /** @returns {number} */
  get doubled() {
    return Math.imul(this.#count, 2);
  }

  /** @param {number} value */
  set doubled(value) {
    this.#count = (value - this.#count) | 0;
  }

  /** @param {number} value */
  set added(value) {
    this.#count = (this.#count + value) | 0;
  }

  /** @returns {Counter} */
  bump() {
    this.#count = (this.#count + 1) | 0;
    this.#hidden = (this.#hidden + 1) | 0;
    return this;
  }

  /**
   * @param {Counter} other
   * @returns {boolean}
   */
  same(other) {
    return this.#hidden === other.#hidden;
  }

  /** @returns {Counter} */
  static zero() {
    return new Counter();
  }
}

class Point extends type() {
  /** @type {number} */
  #x;
  /** @type {number} */
  #y;

  /**
   * @param {{ x: number, y: number }} props
   * @returns {Point}
   */
  static new(props) {
    return new Point(props.x, props.y);
  }

  /**
   * @param {number} x
   * @param {number} y
   */
  constructor(x, y) {
    super();
    this.#x = x;
    this.#y = y;
  }

  /** @returns {number} */
  get x() {
    return this.#x;
  }

  /** @returns {number} */
  get y() {
    return this.#y;
  }

  /**
   * @param {Point} other
   * @returns {boolean}
   */
  eq(other) {
    if (this.#x === other.#x) {
      return this.#y === other.#y;
    } else {
      return false;
    }
  }
}

class Loose extends type() {
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

  /**
   * @param {Loose} other
   * @returns {boolean}
   */
  eq(other) {
    if (this.#n === other.#n) {
      return true;
    } else {
      return this.#n === (-other.#n | 0);
    }
  }

  /**
   * @param {Loose} other
   * @returns {boolean}
   */
  mirrors(other) {
    if (other.eq(this)) {
      return this.eq(other);
    } else {
      return false;
    }
  }
}

/** @template T */
class Boxed extends type() {
  /** @type {T} */
  #value;
  /** @type {number} */
  #tag;

  /**
   * @template T
   * @param {{ value: T, tag: number }} props
   * @returns {Boxed<T>}
   */
  static new(props) {
    return new Boxed(props.value, props.tag);
  }

  /**
   * @param {T} value
   * @param {number} tag
   */
  constructor(value, tag) {
    super();
    this.#value = value;
    this.#tag = tag;
  }

  /** @returns {T} */
  get value() {
    return this.#value;
  }

  /** @returns {number} */
  get tag() {
    return this.#tag;
  }

  /**
   * @param {Boxed<T>} other
   * @returns {boolean}
   */
  eq(other) {
    return this.#tag === other.#tag;
  }

  /**
   * @param {Boxed<T>} other
   * @returns {boolean}
   */
  sameAs(other) {
    if (this.eq(other)) {
      return other.eq(this);
    } else {
      return false;
    }
  }
}

class Holder extends type() {
  /** @type {Loose} */
  #loose;
  /** @type {string} */
  #tag;

  /**
   * @param {{ loose: Loose, tag: string }} props
   * @returns {Holder}
   */
  static new(props) {
    return new Holder(props.loose, props.tag);
  }

  /**
   * @param {Loose} loose
   * @param {string} tag
   */
  constructor(loose, tag) {
    super();
    this.#loose = loose;
    this.#tag = tag;
  }

  /** @returns {Loose} */
  get loose() {
    return this.#loose;
  }

  /** @returns {string} */
  get tag() {
    return this.#tag;
  }

  /**
   * @param {Holder} other
   * @returns {boolean}
   */
  eq(other) {
    if (this.#loose.eq(other.#loose)) {
      return this.#tag === other.#tag;
    } else {
      return false;
    }
  }
}

class Version extends type() {
  /** @type {number} */
  #major;
  /** @type {number} */
  #minor;

  /**
   * @param {{ major: number, minor: number }} props
   * @returns {Version}
   */
  static new(props) {
    return new Version(props.major, props.minor);
  }

  /**
   * @param {number} major
   * @param {number} minor
   */
  constructor(major, minor) {
    super();
    this.#major = major;
    this.#minor = minor;
  }

  /** @returns {number} */
  get major() {
    return this.#major;
  }

  /** @returns {number} */
  get minor() {
    return this.#minor;
  }

  /**
   * @param {Version} other
   * @returns {number}
   */
  compareTo(other) {
    if (this.#major !== other.#major) {
      return cmpInt32(this.#major, other.#major);
    } else {
      return cmpInt32(this.#minor, other.#minor);
    }
  }
}

class Stock extends type() {
  /** @type {number} */
  #count;
  /** @type {readonly number[]} */
  #itemsVar;
  /** @type {number} */
  #size;

  /**
   * @param {{ items: readonly number[], count?: number | null }} props
   * @returns {Stock}
   */
  static new(props) {
    return new Stock(props.items, props.count);
  }

  /**
   * @param {readonly number[]} items
   * @param {number | null} [count]
   */
  constructor(items, count) {
    super();
    count ??= items.length;
    this.#count = count;
    this.#itemsVar = items;
    this.#size = items.length;
  }

  /** @returns {number} */
  get count() {
    return this.#count;
  }

  /** @returns {number} */
  get size() {
    return this.#size;
  }

  /** @returns {readonly number[]} */
  get items() {
    return this.#itemsVar;
  }

  /** @param {readonly number[]} value */
  set items(value) {
    this.#itemsVar = value;
  }

  /** @returns {number} */
  sizeNow() {
    return this.items.length;
  }
}

class Sql extends type() {
  /** @type {string[]} */
  #parts;

  constructor() {
    super();
    this.#parts = [];
  }

  /** @param {string} s */
  appendSafe(s) {
    this.#parts.push(s);
  }

  /** @param {string | null} x */
  appendString(x) {
    this.#parts.push(x == null ? "NULL" : `'${x}'`);
  }

  /** @param {number} x */
  appendInt(x) {
    this.#parts.push(x.toString());
  }

  /** @param {Shape} x */
  appendShape(x) {
    this.#parts.push(`shape ${x.area}`);
  }

  /** @param {Rect} x */
  appendRect(x) {
    this.#parts.push(`rect ${x.toString()}`);
  }

  /** @returns {string} */
  get accumulated() {
    return listedJoin(this.#parts, "", (p) => p);
  }
}

Rect$unit = new Rect(1, 1);
const r = new Rect(2, 3);
console.log(r.toString());
console.log(r.area.toString());
const s = shape(/** @type {Shape} */ (/** @type {unknown} */ (r)));
console.log(s.describe());
const q = shape(new Square(4));
console.log(q.describe());
console.log(q.area.toString());
console.log(r.scaled(2).scaled(3).area.toString());
console.log(Rect.unit.scaled(5).toString());
const c = Counter.zero();
c.bump().bump();
console.log(c.count.toString());
c.doubled = 10;
console.log(c.doubled.toString());
console.log(c.same(new Counter().bump().bump()).toString());
c.added = 5;
/** @type {number} */
let t$0;
{
  /** @type {number} */
  let t$1;
  {
    const t = 2;
    c.added = t;
    t$1 = t;
  }
  const t2 = t$1;
  c.added = t2;
  t$0 = t2;
}
const added = t$0;
console.log(`${added} ${c.count}`);
console.log(`${new Point(1, 2).eq(new Point(1, 2))} ${!new Point(1, 2).eq(new Point(2, 1))}`);
console.log(new Holder(new Loose(3), "a").eq(new Holder(new Loose(-3), "a")).toString());
const loose = new Loose(2);
/** @type {boolean} */
let t$2;
{
  const left = maybeLoose(-2);
  t$2 = left == null ? false : left.eq(loose);
}
/** @type {boolean} */
let t$3;
{
  const left2 = loose;
  const right = maybeLoose(0);
  t$3 = right == null ? false : left2.eq(right);
}
/** @type {boolean} */
let t$4;
{
  const left3 = maybeLoose(0);
  t$4 = left3 == null ? false : left3.eq(loose);
}
console.log(`${loose.mirrors(new Loose(-2))} ${t$2} ${t$3} ${!t$4}`);
/** @type {string} */
let t$5;
{
  const subject = maybeLoose(-2);
  t$5 = (subject == null ? false : subject.eq(loose)) ? "loose" : "other";
}
const matched = t$5;
console.log(matched);
/** @type {Boxed<string>} */
const b1 = new Boxed("x", 1);
/** @type {Boxed<string>} */
const b3 = new Boxed("z", 2);
console.log(`${b1.value} ${b1.eq(new Boxed("y", 1))} ${!b1.eq(b3)} ${b1.sameAs(new Boxed("y", 1))} ${b3.sameAs(b1)}`);
console.log(`${new Version(1, 2).compareTo(new Version(1, 3)) < 0} ${new Version(2, 0).compareTo(new Version(1, 9)) >= 0}`);
console.log(`${q instanceof Square} ${s instanceof Square}`);
const sq = requireInstanceOf(q, Square);
console.log(sq.area.toString());
if (s instanceof Rect) {
  console.log(/** @type {Rect} */ (/** @type {unknown} */ (s)).w.toString());
}
const st = new Stock(Object.freeze([4, 5]));
st.items = Object.freeze([1, 2, 3]);
console.log(`${st.count} ${st.size} ${st.sizeNow()} ${st.items.length}`);
console.log(compared((a, b) => a == null ? b == null : b == null ? false : a.eq(b)));
console.log(compared((a, b) => !(a == null ? b == null : b == null ? false : a.eq(b))));
console.log(compared((a, b) => b == null ? a == null : a == null ? false : b.eq(a)));

/**
 * @param {Loose | null} a
 * @param {Loose | null} b
 * @returns {boolean}
 */
function fn4(a, b) {
  if (a == null) {
    return false;
  }
  if (b == null) {
    return false;
  }
  return a.mirrors(b);
}

console.log(compared(fn4));
const favorite = same("bigint");
let named = 0;
{
  let i = 0;
  while (i < 6) {
    typeNamed(i);
    named = (named + 1) | 0;
    i = (i + 1) | 0;
  }
}
console.log(`${named} types`);
const sql = new Sql();
sql.appendString("it");
sql.appendString(null);
sql.appendInt(3);
sql.appendShape(new Square(2));
sql.appendRect(new Rect(1, 2));
console.log(sql.accumulated);
const seven = 7;
/** @type {string} */
let t$6;
{
  const accumulator = new Sql();
  {
    accumulator.appendSafe("SELECT ");
    accumulator.appendInt(seven);
    accumulator.appendSafe(", ");
    accumulator.appendString("x");
    accumulator.appendSafe(" FROM ");
    accumulator.appendRect(new Rect(2, 2));
  }
  t$6 = accumulator.accumulated;
}
console.log(t$6);
