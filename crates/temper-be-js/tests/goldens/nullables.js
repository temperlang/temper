import {
  bubble,
  divIntInt,
  panic,
  stringToInt32,
  type,
} from "@temperlang/core";

/**
 * @param {number | null} x
 * @returns {string}
 */
export function describe(x) {
  if (x == null) {
    return "none";
  }
  return `some ${(x + 1) | 0}`;
}

/**
 * @param {string | null} s
 * @returns {string | null}
 */
export function firstWord(s) {
  if (s != null && s !== "") {
    return s;
  } else {
    return null;
  }
}

/**
 * @param {string | null} a
 * @param {string | null} b
 * @returns {boolean}
 */
function same(a, b) {
  return a === b;
}

/**
 * @param {number | null} a
 * @param {number | null} b
 * @returns {boolean}
 */
function sameInt(a, b) {
  return a === b;
}

/**
 * @param {string} s
 * @returns {number}
 */
function parse(s) {
  try {
    return stringToInt32(s, null);
  } catch {
    return -1;
  }
}

/**
 * @param {string} s
 * @param {number} d
 * @returns {number}
 */
function halved(s, d) {
  try {
    return divIntInt(stringToInt32(s, null), d);
  } catch {
    return -1;
  }
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
 * @param {number} n
 * @returns {number}
 */
function checked(n) {
  try {
    return positive(n);
  } catch {
    return panic();
  }
}

/** @returns {number} */
export function stops() {
  /** @type {number} */
  let t$0;
  {
    panic();
    t$0 = 1;
  }
  return t$0;
}

/**
 * @param {number | null} x
 * @returns {number}
 */
function orZero(x) {
  {
    if (x == null) {
      return 0;
    } else {
      return x;
    }
  }
}

/**
 * @param {number} n
 * @param {string | null} why
 * @returns {string}
 */
function reason(n, why) {
  try {
    positive(n);
    return "ok";
  } catch {
    return why == null ? "fails" : why;
  }
}

function varying() {
  /** @type {number | null} */
  let maybe = null;
  console.log((maybe == null).toString());
  maybe = 3;
  console.log((maybe != null).toString());
}

export class Node extends type() {
  /** @type {number} */
  #value;
  /** @type {Node | null} */
  #next;

  /**
   * @param {{ value: number, next: Node | null }} props
   * @returns {Node}
   */
  static new(props) {
    return new Node(props.value, props.next);
  }

  /**
   * @param {number} value
   * @param {Node | null} next
   */
  constructor(value, next) {
    super();
    this.#value = value;
    this.#next = next;
  }

  /** @returns {number} */
  get value() {
    return this.#value;
  }

  /** @returns {Node | null} */
  get next() {
    return this.#next;
  }

  /** @returns {number} */
  last() {
    const n = this.#next;
    if (n == null) {
      return this.#value;
    } else {
      return n.last();
    }
  }
}

console.log(describe(null));
console.log(describe(41));
console.log(firstWord("hi") ?? "nothing");
console.log(firstWord("") ?? "nothing");
console.log(`${same(null, null)} ${same("a", null)} ${same(null, "a")} ${same("a", "a")}`);
console.log(`${sameInt(1, 2)} ${sameInt(3, 3)} ${sameInt(null, 3)}`);
console.log(`${parse("12")} ${parse("x")}`);
console.log(`${halved("12", 2)} ${halved("x", 2)} ${halved("12", 0)}`);
console.log(checked(7).toString());
console.log(`${orZero(null)} ${orZero(5)}`);
varying();
const list = new Node(1, new Node(2, new Node(3, null)));
console.log(list.last().toString());
const tail = list.next;
if (tail != null) {
  console.log(tail.value.toString());
}
try {
  positive(-1);
} catch {
  console.log("negative");
}
/** @type {number} */
let fallback;
try {
  fallback = positive(-2);
} catch {
  fallback = 0;
}
console.log(fallback.toString());
console.log(`${reason(1, null)} ${reason(-1, null)} ${reason(-1, "bad")}`);
