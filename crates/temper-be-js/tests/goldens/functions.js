import {
  adaptAwaiter,
  listedFilter,
  listedGet,
  listedJoin,
  listedMap,
  listedSorted,
  stringGet,
  stringNext,
  stringPrev,
  type,
} from "@temperlang/core";

/**
 * @param {number} start
 * @returns {() => number}
 */
function makeCounter(start) {
  let count = start;
  /** @returns {number} */
  function fn() {
    count = (count + 1) | 0;
    return count;
  }
  return fn;
}

/**
 * @param {(arg0: number) => number} f
 * @returns {(arg0: number) => number}
 */
function twice(f) {
  return (x) => f(f(x));
}

/**
 * @param {(arg0: number) => number} f
 * @param {number} x
 * @returns {number}
 */
function applyTo(f, x) {
  return f(x);
}

/**
 * @param {number} x
 * @returns {number}
 */
function addOne(x) {
  return (x + 1) | 0;
}

/**
 * @param {number} steps
 * @param {() => Generator<{}>} body
 */
function drive(steps, body) {
  /** @type {Generator<{}>} */
  const g = body();
  {
    let s = 0;
    while (s < steps) {
      g.next();
      s = (s + 1) | 0;
    }
  }
}

/**
 * @param {readonly number[]} xs
 * @param {number} least
 * @returns {number}
 */
function sumFrom(xs, least) {
  let sum = 0;
  {
    const length = xs.length;
    let i = 0;
    while (i < length) {
      const x = listedGet(/** @type {number[]} */ (xs), i);
      i = (i + 1) | 0;
      if (x < least) {
        continue;
      }
      sum = (sum + x) | 0;
    }
  }
  return sum;
}

/**
 * @param {string} s
 * @returns {number}
 */
function doubles(s) {
  let n = 0;
  const end = s.length;
  {
    let i = 0;
    let prev = i;
    while (i - end < 0) {
      if (prev - i < 0 && stringGet(s, prev) === stringGet(s, i)) {
        n = (n + 1) | 0;
      }
      prev = i;
      i = stringNext(s, i);
    }
  }
  return n;
}

/**
 * @param {number} x
 * @returns {number}
 */
function climb(x) {
  /** @type {number} */
  let d;
  if (x === 0) {
    /**
     * @param {number} n
     * @returns {number}
     */
    function down(n) {
      if (n <= x) {
        return 0;
      } else {
        return (down((n - 1) | 0) + 1) | 0;
      }
    }
    d = down(3);
  } else {
    return -1;
  }
  d = (d + 1) | 0;
  {
    if (d === 4) {
      const top = (d + 1) | 0;
      /**
       * @param {number} n
       * @returns {number}
       */
      function up(n) {
        if (n >= top) {
          return n;
        } else {
          return up((n + 1) | 0);
        }
      }
      return up(d);
    } else {
      return d;
    }
  }
}

/**
 * @param {string} s
 * @returns {boolean}
 */
function stringIsPalindrome(s) {
  let i = 0;
  let j = s.length;
  while (i - j < 0) {
    j = stringPrev(s, j);
    if (stringGet(s, i) !== stringGet(s, j)) {
      return false;
    }
    i = stringNext(s, i);
  }
  return true;
}

/**
 * @param {number | null} n
 * @returns {number}
 */
function intOrZero(n) {
  {
    if (n == null) {
      return 0;
    } else {
      return n;
    }
  }
}

/**
 * @template T
 * @param {readonly T[]} ls
 * @param {T} d
 * @returns {T}
 */
function listFirstOr(ls, d) {
  if (!ls.length) {
    return d;
  } else {
    return listedGet(/** @type {T[]} */ (ls), 0);
  }
}

/**
 * @param {string} s
 * @param {number} n
 * @param {string | null} [fill]
 * @returns {string}
 */
function stringPadded(s, n, fill) {
  fill ??= ".";
  let out = s;
  {
    let i = 0;
    while (i < n) {
      out = out + fill;
      i = (i + 1) | 0;
    }
  }
  return out;
}

/**
 * @param {Gate} g
 * @param {string} s
 * @returns {string}
 */
function gatePassed(g, s) {
  return `${s} ${g.allows(1)}`;
}

class Scaler extends type() {
  /**
   * @param {number} x
   * @returns {number}
   */
  static triple(x) {
    return Math.imul(x, 3);
  }
}

class Button extends type() {
  /** @type {string} */
  #label;
  /** @type {() => number} */
  #onPress;

  /**
   * @param {{ label: string, onPress: () => number }} props
   * @returns {Button}
   */
  static new(props) {
    return new Button(props.label, props.onPress);
  }

  /**
   * @param {string} label
   * @param {() => number} onPress
   */
  constructor(label, onPress) {
    super();
    this.#label = label;
    this.#onPress = onPress;
  }

  /** @returns {string} */
  get label() {
    return this.#label;
  }

  /** @returns {() => number} */
  get onPress() {
    return this.#onPress;
  }

  /** @returns {string} */
  press() {
    return `${this.#label} ${this.#onPress()}`;
  }
}

class Gate extends type() {
  /** @type {((arg0: number) => number) | null} */
  #check;

  constructor() {
    super();
    this.#check = null;
  }

  /** @returns {((arg0: number) => number) | null} */
  get check() {
    return this.#check;
  }

  /** @param {((arg0: number) => number) | null} value */
  set check(value) {
    this.#check = value;
  }

  /**
   * @param {number} n
   * @returns {boolean}
   */
  allows(n) {
    const c = this.#check;
    if (c == null) {
      return true;
    } else {
      return c(n) > 0;
    }
  }

  /**
   * @param {number} n
   * @returns {number}
   */
  passed(n) {
    const c = this.#check;
    if (c != null) {
      return c(n);
    } else {
      return n;
    }
  }
}

const counter = makeCounter(10);
counter();
console.log(`count ${counter()}`);
const other = makeCounter(0);
console.log(`other ${other()} count ${counter()}`);
const addTwo = twice(addOne);
console.log(`twice ${addTwo(5)} ${applyTo(addOne, 1)}`);
console.log(`triple ${applyTo(Scaler.triple, 4)} ${twice(Scaler.triple)(2)}`);
/** @type {readonly number[]} */
const ns = Object.freeze([5, 2, 8, 1, 4]);
/** @type {readonly number[]} */
const doubled = listedMap(/** @type {number[]} */ (ns), (n) => Math.imul(n, 2));
/** @type {readonly number[]} */
const big = listedFilter(/** @type {number[]} */ (doubled), (n) => n > 4);
console.log(listedJoin(/** @type {number[]} */ (big), ", ", (n) => n.toString()));
/** @type {readonly number[]} */
const order = listedSorted(/** @type {number[]} */ (ns), (a, b) => (a - b) | 0);
console.log(listedJoin(/** @type {number[]} */ (order), " ", (n) => n.toString()));
let total = 0;
{
  const length = ns.length;
  let i2 = 0;
  while (i2 < length) {
    const n = listedGet(/** @type {number[]} */ (ns), i2);
    i2 = (i2 + 1) | 0;
    total = (total + n) | 0;
  }
}
console.log(`total ${total}`);
const button = new Button("hits", makeCounter(0));
button.press();
console.log(button.press());
/** @type {(() => number)[]} */
const makers = [];
{
  let i = 0;
  while (i < 3) {
    const k = Math.imul(i, 10);
    makers.push(() => k);
    i = (i + 1) | 0;
  }
}
console.log(listedJoin(makers, " ", (c) => c().toString()));

/**
 * @param {(p: Promise<unknown>) => void} doAwait
 * @returns {Generator<any>}
 */
function* fn8(doAwait) {
  /** @type {(() => number)[]} */
  const fs = [];
  {
    let i = 0;
    while (i < 3) {
      const j = Math.imul(i, 10);
      fs.push(() => j);
      yield;
      fs.push(() => (j + 1) | 0);
      i = (i + 1) | 0;
    }
  }
  console.log(listedJoin(fs, " ", (c) => c().toString()));
  yield;
}

drive(5, adaptAwaiter(fn8));
console.log(`sum ${sumFrom(ns, 4)} doubles ${doubles("bookkeeper")}`);
console.log(`climb ${climb(0)} ${climb(1)}`);
const gate = new Gate();
console.log(`gate ${gate.allows(0)} ${gate.passed(4)}`);
gate.check = (x) => (x - 1) | 0;
console.log(`gate ${gate.allows(1)} ${gate.allows(2)} ${gate.passed(4)}`);
const none = gate.allows(0) ? 3 : null;
console.log(`${stringIsPalindrome("step on no pets")} ${stringIsPalindrome("steps")}`);
console.log(`${intOrZero(none)} ${listFirstOr(Object.freeze([4, 5]), 0)} ${listFirstOr(Object.freeze(["x"]), "-")}`);
console.log(`${stringPadded("ab", 2)} ${stringPadded("ab", 1, "*")}`);
console.log(`${gate.passed(4)} ${gatePassed(gate, "gate")}`);
