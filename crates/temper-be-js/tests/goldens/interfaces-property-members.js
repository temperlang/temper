import { cmpString, listedGet, pairConstructor, type } from "@temperlang/core";

/**
 * @template {I} IT
 * @param {IT} a
 * @param {IT} b
 * @returns {IT}
 */
function leastX(a, b) {
  if (cmpString(a.x, b.x) < 0) {
    return a;
  } else {
    return b;
  }
}

export class I extends type() {
  /** @type {string} */
  x = /** @type {any} */ (undefined);
}

export class J extends type(I) {
  /** @type {string} */
  x = /** @type {any} */ (undefined);
}

class C extends type(J) {
  /** @type {string} */
  #x;

  /** @param {string} x */
  constructor(x) {
    super();
    this.#x = x;
  }

  /** @returns {string} */
  get x() {
    return this.#x;
  }

  /** @param {string} value */
  set x(value) {
    this.#x = value;
  }
}

/** @template {I} T */
class D extends type() {
  /** @type {T} */
  #thing;

  /** @param {T} thing */
  constructor(thing) {
    super();
    this.#thing = thing;
  }

  /** @returns {T} */
  get thing() {
    return this.#thing;
  }
}

/** @template {I} T */
export class SI extends type() {
  /** @type {T} */
  thing = /** @type {any} */ (undefined);
}

/** @template {I} T */
class SSub extends type(SI) {
  /** @type {T} */
  #thing;

  /** @param {T} thing */
  constructor(thing) {
    super();
    this.#thing = thing;
  }

  /** @returns {T} */
  get thing() {
    return this.#thing;
  }
}

/** @template T */
class Holder extends type() {
  /** @type {T} */
  held = /** @type {any} */ (undefined);

  /**
   * @param {T} other
   * @returns {readonly T[]}
   */
  heldAnd(other) {
    return Object.freeze([this.held, other]);
  }
}

/**
 * @template L
 * @template T
 */
class Labeled extends type(Holder) {
  /** @type {(l: L) => string} */
  label = /** @type {any} */ (undefined);
  /** @type {T} */
  held = /** @type {any} */ (undefined);
  /** @type {(other: T) => readonly T[]} */
  heldAnd = /** @type {any} */ (undefined);
}

class LabeledSub extends type(Labeled) {
  /** @type {C} */
  #held;

  /** @param {C} held */
  constructor(held) {
    super();
    this.#held = held;
  }

  /** @returns {C} */
  get held() {
    return this.#held;
  }

  /**
   * @param {string} l
   * @returns {string}
   */
  label(l) {
    return `${l}: ${this.#held.x}`;
  }
}

class High extends type() {
  /** @type {() => string} */
  toString = /** @type {any} */ (undefined);
  /** @type {(other: High) => boolean} */
  eq = /** @type {any} */ (undefined);
}

class Low1 extends type(High) {
  /** @type {number} */
  #i;

  /** @param {number} i */
  constructor(i) {
    super();
    this.#i = i;
  }

  /** @returns {number} */
  get i() {
    return this.#i;
  }

  /** @returns {string} */
  toString() {
    return `Low1(${this.#i})`;
  }

  /**
   * @param {High} other
   * @returns {boolean}
   */
  eq(other) {
    if (other instanceof Low1) {
      return this.#i === /** @type {Low1} */ (/** @type {unknown} */ (other)).#i;
    } else {
      return false;
    }
  }
}

class Mid extends type(High) {
  /** @type {() => string} */
  toString = /** @type {any} */ (undefined);
  /** @type {(other: High) => boolean} */
  eq = /** @type {any} */ (undefined);
}

class Low2 extends type(Mid) {
  /** @type {string} */
  #s;

  /** @param {string} s */
  constructor(s) {
    super();
    this.#s = s;
  }

  /** @returns {string} */
  get s() {
    return this.#s;
  }

  /** @returns {string} */
  toString() {
    return `Low2(${this.#s})`;
  }

  /**
   * @param {High} other
   * @returns {boolean}
   */
  eq(other) {
    if (other instanceof Low2) {
      return this.#s === /** @type {Low2} */ (/** @type {unknown} */ (other)).#s;
    } else {
      return false;
    }
  }
}

class Low3 extends type(Mid) {
  /** @returns {string} */
  toString() {
    return "Low3";
  }

  /**
   * @param {High} other
   * @returns {boolean}
   */
  eq(other) {
    return other instanceof Low3;
  }
}

const a = new C("foo");
const b = new C("bar");
console.log(`leastX of ${a.x} and ${b.x} is ${leastX(a, b).x}.`);
const i = a;
console.log(`As an I, a.x is ${i.x}.`);
/** @type {D<I>} */
const di = new D(a);
/** @type {D<J>} */
const dj = new D(a);
console.log(`Generically, x is ${di.thing.x}.`);
console.log(`Genericallier, x is still ${dj.thing.x}.`);
/** @type {SI<C>} */
const si = new SSub(a);
console.log(`Sealed thing is ${si.thing.x}.`);
/** @type {Labeled<string, C>} */
const labeled = /** @type {Labeled<string, C>} */ (/** @type {unknown} */ (new LabeledSub(a)));
/** @type {readonly C[]} */
const heldAnd = labeled.heldAnd(b);
console.log(`Labeled thing is ${labeled.label("first")}.`);
console.log(`Held are ${labeled.held.x} and ${listedGet(/** @type {C[]} */ (heldAnd), 1).x}.`);
const high1 = new Low1(11);
const high1b = new Low1(12);
const high1c = new Low1(12);
const high2 = new Low2("two");
const high2b = new Low2("to be");
const high2c = new Low2("to be");
const high3 = new Low3();
const high3b = new Low3();
/** @type {readonly import("@temperlang/core/pair.js").Pair<High, High>[]} */
const pairs = Object.freeze([pairConstructor(high1, high1b), pairConstructor(high1b, high1c), pairConstructor(high1, high2), pairConstructor(high2, high2b), pairConstructor(high2b, high2c), pairConstructor(high1, high3), pairConstructor(high3, high3b)]);
{
  const length = pairs.length;
  let i2 = 0;
  while (i2 < length) {
    /** @type {import("@temperlang/core/pair.js").Pair<High, High>} */
    const pair = listedGet(/** @type {import("@temperlang/core/pair.js").Pair<High, High>[]} */ (pairs), i2);
    i2 = (i2 + 1) | 0;
    console.log(`${pair.key.toString()} == ${pair.value.toString()}: ${pair.key.eq(pair.value)}`);
  }
}
