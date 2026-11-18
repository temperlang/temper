import { bubble, type } from "@temperlang/core";

/**
 * @param {number} x
 * @returns {number}
 */
export function good(x) {
  return (x + 1) | 0;
}

/**
 * @param {number} x
 * @returns {number}
 */
function mismatched(x) {
  const s = x;
  return 1;
}

/** @returns {number} */
function undeclared() {
  return bubble();
}

/**
 * @param {string} s
 * @returns {number}
 */
function misspelled(s) {
  return bubble();
}

/**
 * @param {string} s
 * @returns {number}
 */
function misspelledMethod(s) {
  return bubble();
}

/** @returns {number} */
function wrongArity() {
  return bubble();
}

/**
 * @param {unknown} x
 * @returns {number}
 */
function unknownType(x) {
  return 1;
}

/**
 * @param {number} x
 * @returns {number}
 */
function afterUnclosed(x) {
  return Math.imul(x, 3);
}

/**
 * @param {number} cents
 * @returns {string}
 */
function dropped(cents) {
  bubble();
}

/**
 * @param {number} x
 * @returns {string}
 */
function stored(x) {
  const s = x;
  return `${s}!`;
}

/** @returns {number} */
function returned() {
  return "text";
}

/**
 * @param {number} x
 * @returns {number}
 */
function passedOn(x) {
  return x;
}

class Box extends type() {
  /** @type {number} */
  #value;

  /** @param {number} value */
  constructor(value) {
    super();
    this.#value = value;
  }

  /** @returns {number} */
  get value() {
    return this.#value;
  }

  /** @returns {number} */
  get() {
    return this.#value;
  }

  /** @returns {string} */
  bad() {
    return this.#value;
  }
}

/** @type {number} */
let a;
try {
  a = mismatched(1);
} catch {
  a = -1;
}
/** @type {number} */
let b;
try {
  b = undeclared();
} catch {
  b = -1;
}
/** @type {number} */
let c;
try {
  c = misspelled("a");
} catch {
  c = -1;
}
/** @type {number} */
let d;
try {
  d = misspelledMethod("a");
} catch {
  d = -1;
}
/** @type {number} */
let e;
try {
  e = wrongArity();
} catch {
  e = -1;
}
const box = new Box(3);
/** @type {string} */
let f;
try {
  f = box.bad();
} catch {
  f = "caught";
}
console.log(`${good(1)} ${a} ${b} ${c} ${d} ${e}`);
console.log(`${unknownType(1)} ${afterUnclosed(2)} ${box.get()} ${f}`);
/** @type {string} */
let g;
try {
  g = stored(1);
} catch {
  g = "caught";
}
/** @type {number} */
let h;
try {
  h = returned();
} catch {
  h = -1;
}
/** @type {number} */
let i;
try {
  i = passedOn("in");
} catch {
  i = -1;
}
/** @type {string} */
let j;
try {
  j = dropped(1);
} catch {
  j = "dropped";
}
console.log(`${g} ${h} ${i} ${j}`);
