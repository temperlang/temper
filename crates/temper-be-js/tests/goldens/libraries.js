import {
  bubble,
  cmpFloat64,
  cmpString,
  divIntInt,
  float64ToString,
  listBuilderToList,
  listedGet,
  listedJoin,
  listedMap,
  panic,
  stringToFloat64,
  stringToInt32,
  type,
} from "@temperlang/core";

/**
 * @param {string} s
 * @returns {Grid}
 */
export function parseGrid(s) {
  if (s === "") {
    bubble();
  }
  return new Grid(1, Object.freeze([stringToFloat64(s)]));
}

/**
 * @param {string | null} [label]
 * @returns {Tagged}
 */
function tagged(label) {
  label ??= null;
  return new Tagged(label);
}

/**
 * @param {number} c
 * @returns {number}
 */
function weight(c) {
  const base = c === 42 ? 0 : 6;
  return ((((c > 50 ? 1 : 2) + base) | 0) + c) | 0;
}

/**
 * @param {Shape} s
 * @returns {string}
 */
function shapeName(s) {
  const name = s instanceof Dot ? "dot" : s instanceof Line ? "line" : panic();
  return name;
}

/**
 * @param {number} n
 * @returns {Shape}
 */
function shapeOf(n) {
  const shape = n > 1 ? new Line(n) : new Dot(n);
  return shape;
}

/**
 * @param {Shape} s
 * @returns {readonly number[]}
 */
function sizes(s) {
  /** @type {readonly number[]} */
  const found = s instanceof Dot ? Object.freeze([/** @type {Dot} */ (/** @type {unknown} */ (s)).size, /** @type {Dot} */ (/** @type {unknown} */ (s)).size]) : Object.freeze([]);
  return found;
}

/**
 * @param {readonly number[]} values
 * @param {number} ncols
 * @returns {number}
 */
function rows(values, ncols) {
  try {
    return divIntInt(values.length, ncols);
  } catch {
    return panic();
  }
}

/**
 * @param {Grid} grid
 * @returns {Grid}
 */
function scaled(grid) {
  if (grid.ncols === 0) {
    bubble();
  }
  return grid;
}

/**
 * @param {Grid} grid
 * @returns {readonly number[]}
 */
function cubes(grid) {
  try {
    /** @type {readonly number[]} */
    const more = listedMap(/** @type {number[]} */ (scaled(grid).values), (x) => x * x * x);
    return more;
  } catch {
    return panic();
  }
}

/**
 * @param {readonly number[]} values
 * @param {(arg0: number) => void} f
 */
function each(values, f) {
  {
    let i = 0;
    while (i < values.length) {
      f(listedGet(/** @type {number[]} */ (values), i));
      i = (i + 1) | 0;
    }
  }
}

/**
 * @param {readonly number[]} values
 * @returns {number}
 */
function total(values) {
  let sum = 0.0;
  /** @param {number} x */
  function fn(x) {
    try {
      const part = x / 2.0;
      sum = sum + part;
    } catch {
      panic();
    }
  }
  each(values, fn);
  return sum;
}

/**
 * @param {number} x
 * @returns {number}
 */
function half(x) {
  try {
    return x / 2.0;
  } catch {
    return panic();
  }
}

/**
 * @param {number} x
 * @returns {number}
 */
function cube(x) {
  return x * 0.25;
}

/**
 * @param {Grid} grid
 * @returns {number}
 */
function corner(grid) {
  try {
    return grid.get(1, 1);
  } catch {
    return -1.0;
  }
}

/**
 * @param {number} n
 * @returns {Grid | null}
 */
function maybe(n) {
  if (n > 0) {
    return new Grid(n, Object.freeze([1.0]));
  } else {
    return null;
  }
}

/**
 * @param {Grid} grid
 * @returns {readonly number[]}
 */
function mapped(grid) {
  /** @type {Grid} */
  let t$0;
  try {
    t$0 = scaled(grid);
  } catch {
    t$0 = panic();
  }
  /**
   * @param {number} x
   * @returns {number}
   */
  function fn(x) {
    if (cmpFloat64(x, 1.5) > 0) {
      return x * 2.0;
    } else {
      return x;
    }
  }
  return listedMap(/** @type {number[]} */ (t$0.values), fn);
}

/**
 * @param {readonly number[]} digits
 * @param {boolean} short
 * @param {number} c
 * @returns {number}
 */
function byteAt(digits, short, c) {
  const byte = short ? Math.imul(listedGet(/** @type {number[]} */ (digits), c), 17) : (Math.imul(listedGet(/** @type {number[]} */ (digits), Math.imul(2, c)), 16) + listedGet(/** @type {number[]} */ (digits), (Math.imul(2, c) + 1) | 0)) | 0;
  return byte;
}

/**
 * @param {readonly number[]} digits
 * @returns {boolean}
 */
function ascending(digits) {
  if (digits.length > 1) {
    return ((listedGet(/** @type {number[]} */ (digits), 0) + 1) | 0) === listedGet(/** @type {number[]} */ (digits), 1);
  } else {
    return false;
  }
}

/**
 * @param {readonly number[]} digits
 * @returns {number}
 */
function firstDrop(digits) {
  let i = 1;
  while (i < digits.length && listedGet(/** @type {number[]} */ (digits), (i - 1) | 0) <= listedGet(/** @type {number[]} */ (digits), i)) {
    i = (i + 1) | 0;
  }
  return i;
}

/** @returns {number} */
function tick() {
  ticks = (ticks + 1) | 0;
  return ticks;
}

/**
 * @param {boolean} short
 * @returns {number}
 */
function tickIf(short) {
  const n = short ? tick() : (Math.imul(tick(), 10) + tick()) | 0;
  return n;
}

/** @returns {boolean} */
function ticked() {
  if (ticks > 0) {
    return ((Math.imul(tick(), 10) + tick()) | 0) > 0;
  } else {
    return false;
  }
}

/** @returns {number} */
function tickWhile() {
  let i = 0;
  while (i < 3 && tick() < tick()) {
    i = (i + 1) | 0;
  }
  return i;
}

/**
 * @template T
 * @param {readonly T[]} items
 * @returns {number}
 */
export function sizeWith(items) {
  return (items.length + extras.length) | 0;
}

/**
 * @param {number} n
 * @returns {string | null}
 */
function text(n) {
  if (n > 0) {
    return "x";
  } else {
    return null;
  }
}

/**
 * @param {number} n
 * @returns {number | null}
 */
function count(n) {
  if (n > 0) {
    return n;
  } else {
    return null;
  }
}

/**
 * @param {number} n
 * @returns {Casing | null}
 */
function casing(n) {
  if (n > 0) {
    return Casing.first;
  } else {
    return null;
  }
}

/**
 * @param {string} s
 * @returns {string}
 */
function captured(s) {
  /** @type {Grid | null} */
  let found;
  try {
    found = parseGrid(s);
  } catch {
    found = null;
  }
  return listedJoin(/** @type {boolean[]} */ (listedMap(/** @type {number[]} */ (Object.freeze([1, 2])), (x) => found == null)), ",", (b) => b.toString());
}

/** @returns {string} */
function bidi() {
  return "look\u202eout";
}

/**
 * @param {number} n
 * @returns {string}
 */
function word(n) {
  if (n > 1) {
    return "b";
  } else {
    return "a";
  }
}

/**
 * @template T
 * @param {Effect<T>[]} into
 * @param {readonly Effect<T>[]} more
 * @returns {number}
 */
function joinEffects(into, more) {
  {
    const length = more.length;
    let i = 0;
    while (i < length) {
      /** @type {Effect<T>} */
      const e = listedGet(/** @type {Effect<T>[]} */ (more), i);
      i = (i + 1) | 0;
      into.push(e);
    }
  }
  return into.length;
}

/**
 * @template T
 * @param {string} text
 * @param {T} event
 * @param {T} other
 * @returns {string}
 */
export function effectsOf(text, event, other) {
  /** @type {Effect<T>[]} */
  const into = [];
  joinEffects(into, Object.freeze([]));
  joinEffects(into, Object.freeze([new Plain(text)]));
  joinEffects(into, Object.freeze([new Happened(event), new Plain("!"), new Happened(other)]));
  let shown = "";
  {
    /** @type {readonly Effect<T>[]} */
    const list = listBuilderToList(into);
    const length = list.length;
    let i = 0;
    while (i < length) {
      /** @type {Effect<T>} */
      const e = listedGet(/** @type {Effect<T>[]} */ (list), i);
      i = (i + 1) | 0;
      if (e instanceof Plain) {
        shown = shown + /** @type {Plain<T>} */ (/** @type {unknown} */ (e)).text;
      } else {
        shown = `${shown}*`;
      }
    }
  }
  return shown;
}

/**
 * @template {Shown} T
 * @param {Held<T> | null} a
 * @param {Held<T> | null} b
 * @returns {boolean}
 */
export function sameHeld(a, b) {
  /** @type {boolean} */
  let t$1;
  {
    /** @type {Held<T> | null} */
    const left = a;
    /** @type {Held<T> | null} */
    const right = b;
    t$1 = left == null ? right == null : right == null ? false : left === right;
  }
  return t$1;
}

/**
 * @param {number} n
 * @returns {string | null}
 */
function problem(n) {
  const p = n === 0 ? null : panic();
  return p;
}

/**
 * @param {number} n
 * @returns {string | null}
 */
function message(n) {
  const p = n === 0 ? null : n === 1 ? "one" : n === 2 ? null : panic();
  return p;
}

/**
 * @param {readonly string[]} xs
 * @returns {number}
 */
function sumParsed(xs) {
  let total = 0;
  {
    let i = 0;
    while (i < xs.length) {
      const x = listedGet(/** @type {string[]} */ (xs), i);
      /** @type {number} */
      let n;
      /** @type {number | null} */
      let held = null;
      try {
        held = stringToInt32(x, null);
      } catch {
      }
      if (held == null) {
        {
          i = (i + 1) | 0;
          continue;
        }
      } else {
        n = held;
      }
      total = (total + n) | 0;
      i = (i + 1) | 0;
    }
  }
  {
    const length = xs.length;
    let i = 0;
    while (i < length) {
      const x = listedGet(/** @type {string[]} */ (xs), i);
      i = (i + 1) | 0;
      /** @type {number} */
      let m;
      /** @type {number | null} */
      let held = null;
      try {
        held = stringToInt32(x, null);
      } catch {
      }
      if (held == null) {
        continue;
      } else {
        m = held;
      }
      total = (total + Math.imul(m, 10)) | 0;
    }
  }
  return total;
}

/**
 * @param {readonly number[]} xs
 * @returns {number}
 */
function backwards(xs) {
  let t = 0;
  {
    let i = xs.length;
    while (true) {
      /** @type {number} */
      let t$2;
      {
        i = (i - 1) | 0;
        t$2 = i;
      }
      if (t$2 < 0) {
        break;
      }
      t = (Math.imul(t, 10) + listedGet(/** @type {number[]} */ (xs), i)) | 0;
    }
  }
  return t;
}

export class Grid extends type() {
  /** @type {number} */
  #ncols;
  /** @type {readonly number[]} */
  #values;

  /**
   * @param {{ ncols: number, values: readonly number[] }} props
   * @returns {Grid}
   */
  static new(props) {
    return new Grid(props.ncols, props.values);
  }

  /**
   * @param {number} ncols
   * @param {readonly number[]} values
   */
  constructor(ncols, values) {
    super();
    this.#ncols = ncols;
    this.#values = values;
  }

  /** @returns {number} */
  get ncols() {
    return this.#ncols;
  }

  /** @returns {readonly number[]} */
  get values() {
    return this.#values;
  }

  /**
   * @param {number} i
   * @param {number} j
   * @returns {number}
   */
  get(i, j) {
    return listedGet(/** @type {number[]} */ (this.#values), (Math.imul(i, this.#ncols) + j) | 0);
  }

  /**
   * @param {string} s
   * @returns {Grid | null}
   */
  static parse(s) {
    try {
      return parseGrid(s);
    } catch {
      return null;
    }
  }
}

/** @type {Casing} */
let Casing$first;
/** @type {Casing} */
let Casing$last;
/** @type {string} */
let Casing$mark;

export class Casing extends type() {
  /** @type {string} */
  #name;

  /** @returns {Casing} */
  static get first() {
    return Casing$first;
  }

  /** @returns {Casing} */
  static get last() {
    return Casing$last;
  }

  /** @param {string} name */
  constructor(name) {
    super();
    this.#name = name;
  }

  /** @returns {string} */
  get name() {
    return this.#name;
  }

  /**
   * @param {Casing} other
   * @returns {boolean}
   */
  eq(other) {
    return this.#name === other.#name;
  }

  /** @returns {string} */
  toString() {
    return this.#name;
  }

  /**
   * @param {string} name
   * @returns {Casing}
   */
  static named(name) {
    return new Casing(name);
  }

  /** @returns {string} */
  get marked() {
    return Casing$mark + this.#name;
  }
}

export class Shape extends type() {
  /** @type {number} */
  size = /** @type {any} */ (undefined);
}

export class Dot extends type(Shape) {
  /** @type {number} */
  #size;

  /** @param {number} size */
  constructor(size) {
    super();
    this.#size = size;
  }

  /** @returns {number} */
  get size() {
    return this.#size;
  }

  /** @returns {Dot | null} */
  get bigger() {
    if (this.#size < 3) {
      return new Dot((this.#size + 1) | 0);
    } else {
      return null;
    }
  }

  /** @returns {number | null} */
  get grown() {
    {
      const subject = this.bigger;
      if (subject == null) {
        return null;
      } else {
        return subject.scaled(10);
      }
    }
  }

  /**
   * @param {number} k
   * @returns {number}
   */
  scaled(k) {
    return Math.imul(this.#size, k);
  }
}

export class Line extends type(Shape) {
  /** @type {number} */
  #size;

  /** @param {number} size */
  constructor(size) {
    super();
    this.#size = size;
  }

  /** @returns {number} */
  get size() {
    return this.#size;
  }
}

export class Carry extends type() {
  /** @type {boolean} */
  #carrying;
  /** @type {number} */
  #state;

  /** @param {number} start */
  constructor(start) {
    super();
    this.#carrying = false;
    this.#state = 0;
    if (start !== 0) {
      this.#carrying = true;
      this.#state = start;
    }
  }

  /** @returns {boolean} */
  get carrying() {
    return this.#carrying;
  }

  /** @returns {number} */
  get state() {
    return this.#state;
  }
}

export class Tagged extends type() {
  /** @type {string | null} */
  #label;

  /** @param {string | null} label */
  constructor(label) {
    super();
    this.#label = label;
  }

  /** @returns {string | null} */
  get label() {
    return this.#label;
  }
}

export class Word extends type() {
  /** @type {string} */
  #text;

  /** @param {string} text */
  constructor(text) {
    super();
    this.#text = text;
  }

  /** @returns {string} */
  get text() {
    return this.#text;
  }
}

export class Joiner extends type() {
  /** @type {string} */
  #out;

  constructor() {
    super();
    this.#out = "";
  }

  /** @returns {string} */
  get out() {
    return this.#out;
  }

  /** @param {string} value */
  set out(value) {
    this.#out = value;
  }

  /** @param {Word} w */
  addWord(w) {
    this.#out = `${this.#out}w${w.text}`;
  }

  /** @param {readonly boolean[]} x */
  addFlags(x) {
    this.#out = `${this.#out}f${x.length}`;
  }

  /** @param {readonly string[]} x */
  addTexts(x) {
    this.#out = `${this.#out}t${x.length}`;
  }

  /** @param {readonly (string | null)[]} x */
  addMaybes(x) {
    this.#out = `${this.#out}m${x.length}`;
  }
}

export class Shown extends type() {
  /** @type {() => string} */
  toString = /** @type {any} */ (undefined);
}

export class Tag extends type(Shown) {
  /** @type {string} */
  #name;

  /** @param {string} name */
  constructor(name) {
    super();
    this.#name = name;
  }

  /** @returns {string} */
  get name() {
    return this.#name;
  }

  /** @returns {string} */
  toString() {
    return `<${this.#name}>`;
  }
}

/** @template {Shown} T */
export class Held extends type() {
  /** @type {T} */
  #item;
  /** @type {Held<T> | null} */
  #next;

  /**
   * @template {Shown} T
   * @param {{ item: T, next: Held<T> | null }} props
   * @returns {Held<T>}
   */
  static new(props) {
    return new Held(props.item, props.next);
  }

  /**
   * @param {T} item
   * @param {Held<T> | null} next
   */
  constructor(item, next) {
    super();
    this.#item = item;
    this.#next = next;
  }

  /** @returns {T} */
  get item() {
    return this.#item;
  }

  /** @returns {Held<T> | null} */
  get next() {
    return this.#next;
  }

  /** @returns {string} */
  toString() {
    return `Held(${this.#item.toString()})`;
  }
}

/** @template T */
export class Effect extends type() {}

/** @template T */
export class Plain extends type(Effect) {
  /** @type {string} */
  #text;

  /** @param {string} text */
  constructor(text) {
    super();
    this.#text = text;
  }

  /** @returns {string} */
  get text() {
    return this.#text;
  }
}

/** @template T */
export class Happened extends type(Effect) {
  /** @type {T} */
  #event;

  /** @param {T} event */
  constructor(event) {
    super();
    this.#event = event;
  }

  /** @returns {T} */
  get event() {
    return this.#event;
  }
}

/** @template T */
export class Bundle extends type() {
  /** @type {readonly Effect<T>[]} */
  #effects;
  /** @type {T} */
  #first;

  /**
   * @template T
   * @param {{ effects: readonly Effect<T>[], first: T }} props
   * @returns {Bundle<T>}
   */
  static new(props) {
    return new Bundle(props.effects, props.first);
  }

  /**
   * @param {readonly Effect<T>[]} effects
   * @param {T} first
   */
  constructor(effects, first) {
    super();
    this.#effects = effects;
    this.#first = first;
  }

  /** @returns {readonly Effect<T>[]} */
  get effects() {
    return this.#effects;
  }

  /** @returns {T} */
  get first() {
    return this.#first;
  }
}

/** @template T */
export class Stepper extends type() {
  /** @type {() => readonly T[]} */
  step = /** @type {any} */ (undefined);
}

/** @template T */
export class Counting extends type(Stepper) {
  /** @type {number} */
  count = /** @type {any} */ (undefined);
  /** @type {() => T} */
  made = /** @type {any} */ (undefined);

  /** @returns {readonly T[]} */
  step() {
    this.count = (this.count + 1) | 0;
    return Object.freeze([this.made()]);
  }
}

export class Ticks extends type(Counting) {
  /** @type {number} */
  #n;

  constructor() {
    super();
    this.#n = 0;
  }

  /** @returns {number} */
  get count() {
    return this.#n;
  }

  /** @param {number} c */
  set count(c) {
    this.#n = c;
  }

  /** @returns {string} */
  made() {
    return `tick${this.#n}`;
  }
}

/** @template T */
export class Sink extends type() {
  /** @type {(e: T) => number} */
  take = /** @type {any} */ (undefined);
}

export class Names extends type(Sink) {
  /**
   * @param {Tag} e
   * @returns {number}
   */
  take(e) {
    if (e.name === "abc") {
      return 3;
    } else {
      return 0;
    }
  }
}

export class Escapes extends type() {
  /** @type {(s: string | null) => string} */
  escape = /** @type {any} */ (undefined);

  /**
   * @param {boolean} b
   * @returns {string}
   */
  escapeFlag(b) {
    return this.escape(b ? "T" : "F");
  }
}

export class Quoting extends type(Escapes) {
  /**
   * @param {string | null} s
   * @returns {string}
   */
  escape(s) {
    return `'${s == null ? "null" : s}'`;
  }
}

Casing$first = new Casing("first");
Casing$last = Casing.named("last");
Casing$mark = "#";
let ticks = 0;
/** @type {readonly number[]} */
const extras = Object.freeze([1]);
const grid = new Grid(2, Object.freeze([1.0, 2.0, 3.0, 4.0]));
console.log(`${rows(grid.values, 2)} ${float64ToString(half(3.0))} ${float64ToString(cube(8.0))}`);
console.log(float64ToString(corner(grid)));
console.log(float64ToString(total(grid.values)));
const none = text(0);
const some = count(3);
/** @type {string} */
let t$3;
{
  const part2 = some;
  t$3 = part2 == null ? "null" : part2.toString();
}
/** @type {string} */
let t$4;
{
  /** @type {number | null} */
  let t$5;
  {
    const subject = maybe(0);
    t$5 = subject == null ? null : subject.ncols;
  }
  const part3 = t$5;
  t$4 = part3 == null ? "null" : part3.toString();
}
console.log(`${none ?? "null"} ${t$3} ${t$4}`);
console.log(`${maybe(1) != null} ${maybe(0) != null} ${Grid.parse("") == null}`);
console.log(Casing.first.toString());
console.log(listedJoin(/** @type {number[]} */ (mapped(grid)), ",", (x) => float64ToString(x)));
console.log(listedJoin(/** @type {number[]} */ (cubes(grid)), ",", (x) => float64ToString(x)));
/** @type {boolean} */
let t$6;
{
  const left = casing(1);
  t$6 = left == null ? false : left.eq(Casing.first);
}
/** @type {boolean} */
let t$7;
{
  const left2 = casing(0);
  t$7 = left2 == null ? false : left2.eq(Casing.first);
}
/** @type {boolean} */
let t$8;
{
  const left3 = casing(0);
  const right = casing(0);
  t$8 = left3 == null ? right == null : right == null ? false : left3.eq(right);
}
console.log(`${t$6} ${t$7} ${!t$8}`);
console.log(`${captured("")} ${captured("2.5")}`);
console.log((bidi() !== "lookout").toString());
console.log(`${Math.sign(cmpString(word(1), word(2)))} ${Math.sign(cmpString(word(2), word(1)))} ${Math.sign(cmpString(word(2), word(3)))}`);
const named = word(2);
console.log((named === word(3) && `${named}!` !== named).toString());
/** @type {readonly number[]} */
const digits = Object.freeze([1, 2, 4, 3]);
console.log(`${byteAt(digits, true, 1)} ${byteAt(digits, false, 0)} ${ascending(digits)} ${firstDrop(digits)} ${sizeWith(digits)}`);
console.log(`${tickIf(true)} ${tickIf(false)} ${ticked()} ${tickWhile()}`);
/** @type {string} */
let t$9;
{
  const part4 = new Dot(1).grown;
  t$9 = part4 == null ? "null" : part4.toString();
}
/** @type {string} */
let t$10;
{
  const part5 = new Dot(3).grown;
  t$10 = part5 == null ? "null" : part5.toString();
}
console.log(`${shapeName(shapeOf(1))} ${shapeName(shapeOf(2))} ${sizes(shapeOf(1)).length} ${sizes(shapeOf(2)).length} ${t$9} ${t$10} ${Casing.last.toString()} ${Casing.first.marked}`);
const carry = new Carry(3);
console.log(`${carry.carrying} ${carry.state} ${new Carry(0).carrying} ${tagged().label ?? "null"} ${tagged("x").label ?? "null"} ${weight(42)} ${weight(60)}`);
/** @type {readonly boolean[]} */
let t$11;
{
  /** @type {boolean[]} */
  const b = [];
  b.push(true);
  b.push(false);
  t$11 = listBuilderToList(b);
}
/** @type {readonly boolean[]} */
const flags = t$11;
const joiner = new Joiner();
joiner.addWord(new Word("a"));
joiner.addFlags(Object.freeze([true]));
joiner.addTexts(Object.freeze(["x", "y"]));
/** @type {readonly (string | null)[]} */
const maybes = Object.freeze([null, "z"]);
joiner.addMaybes(maybes);
console.log(joiner.out);
/** @type {Held<Tag>} */
const held = new Held(new Tag("a"), null);
/** @type {Held<Tag>} */
const other = new Held(new Tag("b"), held);
/** @type {Held<Tag> | null} */
const nobody = null;
/** @type {Tag | null} */
let t$12;
{
  /** @type {Held<Tag> | null} */
  const subject3 = other.next;
  t$12 = subject3 == null ? null : subject3.item;
}
console.log(`${held.toString()} ${(t$12 ?? new Tag("none")).toString()} ${sameHeld(held, held)} ${sameHeld(held, other)} ${sameHeld(nobody, nobody)} ${sameHeld(nobody, held)}`);
console.log(`${effectsOf("ab", 1, 2)} ${new Bundle(Object.freeze([]), "2").first} ${flags.length}`);
const counter = new Ticks();
/** @type {Counting<string>} */
const stepper = /** @type {Counting<string>} */ (/** @type {unknown} */ (counter));
stepper.step();
console.log(`${listedGet(/** @type {string[]} */ (stepper.step()), 0)} ${counter.count} ${new Names().take(new Tag("abc"))} ${/** @type {Escapes} */ (/** @type {unknown} */ (new Quoting())).escapeFlag(true)}`);
console.log(`${problem(0) ?? "none"} ${message(1) ?? "none"} ${message(2) ?? "none"} ${sumParsed(Object.freeze(["1", "x", "2"]))} ${backwards(Object.freeze([1, 2, 3]))}`);
