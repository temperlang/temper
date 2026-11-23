import {
  listBuilderAddAll,
  listBuilderRemoveLast,
  listBuilderSet,
  listBuilderSort,
  listBuilderSplice,
  listedFilter,
  listedJoin,
  listedMap,
  listedReduceFrom,
  mapBuilderRemove,
  mappedForEach,
  mappedToListWith,
} from "@temperlang/core";

/**
 * @param {readonly number[]} ls
 * @returns {string}
 */
function show(ls) {
  return listedJoin(/** @type {number[]} */ (ls), ",", (n) => n.toString());
}

/** @returns {string[]} */
function words() {
  /** @type {string[]} */
  const w = [];
  {
    let i = 0;
    while (i < 3) {
      w.push(`w${i}`);
      i = (i + 1) | 0;
    }
  }
  return w;
}

/** @returns {number} */
function reduceFromGrowing() {
  /** @type {number[]} */
  const g = Object.freeze([1, 2]).slice();
  /**
   * @param {number} sum
   * @param {number} n
   * @returns {number}
   */
  function fn(sum, n) {
    if (g.length < 4) {
      g.push(9);
    }
    return (Math.imul(sum, 10) + n) | 0;
  }
  return listedReduceFrom(/** @type {number[]} */ (g), 0, fn);
}

/** @returns {number} */
function mapShrinking() {
  /** @type {string[]} */
  const w = words();
  /**
   * @param {string} s
   * @returns {string}
   */
  function fn(s) {
    if (w.length > 1) {
      listBuilderRemoveLast(w);
    }
    return s;
  }
  return listedMap(/** @type {string[]} */ (w), fn).length;
}

/** @returns {number} */
function reduceShrinking() {
  /** @type {string[]} */
  const w = words();
  /**
   * @param {string} all
   * @param {string} s
   * @returns {string}
   */
  function fn(all, s) {
    if (w.length > 1) {
      listBuilderRemoveLast(w);
    }
    return all;
  }
  const all2 = w.reduce(fn);
  if (!all2) {
    return 0;
  } else {
    return 1;
  }
}

/** @returns {number} */
function filterGrowing() {
  /** @type {number[]} */
  const g = Object.freeze([1, 2]).slice();
  /**
   * @param {number} n
   * @returns {boolean}
   */
  function fn(n) {
    if (g.length < 4) {
      g.push(9);
    }
    return true;
  }
  return listedFilter(g, fn).length;
}

/** @returns {number} */
function joinGrowing() {
  /** @type {number[]} */
  const g = Object.freeze([1, 2]).slice();
  /**
   * @param {number} n
   * @returns {string}
   */
  function fn(n) {
    if (g.length < 4) {
      g.push(9);
    }
    return n.toString();
  }
  const joined = listedJoin(g, ",", fn);
  if (!joined) {
    return 0;
  } else {
    return 1;
  }
}

/** @returns {Map<string, number>} */
function letters() {
  /** @type {Map<string, number>} */
  const m = new Map();
  m.set(`a${1}`, 1);
  m.set(`b${2}`, 2);
  m.set(`c${3}`, 3);
  return m;
}

/** @returns {number} */
function forEachRemoving() {
  /** @type {Map<string, number>} */
  const m = letters();
  let sum = 0;
  /**
   * @param {string} k
   * @param {number} v
   */
  function fn(k, v) {
    if (m.has("c3")) {
      try {
        mapBuilderRemove(m, "c3");
      } catch {
      }
    }
    sum = (sum + v) | 0;
  }
  mappedForEach(m, fn);
  return sum;
}

/** @returns {number} */
function forEachAdding() {
  /** @type {Map<string, number>} */
  const m = letters();
  let count = 0;
  /**
   * @param {string} k
   * @param {number} v
   */
  function fn(k, v) {
    count = (count + 1) | 0;
    if (m.size < 30) {
      m.set(`k${m.size}`, 1);
    }
  }
  mappedForEach(m, fn);
  return count;
}

/** @returns {number} */
function toListWithRemoving() {
  /** @type {Map<string, number>} */
  const m = letters();
  /**
   * @param {string} k
   * @param {number} v
   * @returns {number}
   */
  function fn(k, v) {
    if (m.has("c3")) {
      try {
        mapBuilderRemove(m, "c3");
      } catch {
      }
    }
    return v;
  }
  return mappedToListWith(m, fn).length;
}

/** @type {number[]} */
const b = Object.freeze([1, 2]).slice();
listBuilderAddAll(b, /** @type {number[]} */ (b));
/** @type {number[]} */
const c = Object.freeze([1, 2]).slice();
listBuilderAddAll(c, /** @type {number[]} */ (c), 1);
/** @type {number[]} */
const d = Object.freeze([1, 2, 3]).slice();
/** @type {readonly number[]} */
const removed = listBuilderSplice(d, 1, 1, d);
console.log(`self ${show(b)} ${show(c)} ${show(d)} ${show(removed)}`);
/** @type {number[]} */
const e = Object.freeze([1, 2]).slice();
/** @type {readonly number[]} */
const kept = listedFilter(e, (n) => n > 0);
e.push(3);
console.log(`filter ${show(kept)} ${show(e)}`);
/** @type {number[]} */
const f = Object.freeze([3, 1, 2]).slice();
let peeks = 0;

/**
 * @param {number} a
 * @param {number} z
 * @returns {number}
 */
function fn2(a, z) {
  peeks = (peeks + f.length) | 0;
  return (a - z) | 0;
}

listBuilderSort(f, fn2);
console.log(`peeking ${show(f)}`);
/** @type {number} */
let t$0;
try {
  t$0 = reduceFromGrowing();
} catch {
  t$0 = -1;
}
const t$2 = t$0;
/** @type {number} */
let t$1;
try {
  t$1 = mapShrinking();
} catch {
  t$1 = -1;
}
const t$4 = t$2;
const t$5 = t$1;
/** @type {number} */
let t$3;
try {
  t$3 = reduceShrinking();
} catch {
  t$3 = -1;
}
const t$7 = t$4;
const t$8 = t$5;
const t$9 = t$3;
/** @type {number} */
let t$6;
try {
  t$6 = filterGrowing();
} catch {
  t$6 = -1;
}
const t$11 = t$7;
const t$12 = t$8;
const t$13 = t$9;
const t$14 = t$6;
/** @type {number} */
let t$10;
try {
  t$10 = joinGrowing();
} catch {
  t$10 = -1;
}
const t$16 = t$11;
const t$17 = t$12;
const t$18 = t$13;
const t$19 = t$14;
const t$20 = t$10;
/** @type {number} */
let t$15;
try {
  t$15 = forEachRemoving();
} catch {
  t$15 = -1;
}
const t$22 = t$16;
const t$23 = t$17;
const t$24 = t$18;
const t$25 = t$19;
const t$26 = t$20;
const t$27 = t$15;
/** @type {number} */
let t$21;
try {
  t$21 = forEachAdding();
} catch {
  t$21 = -1;
}
const t$29 = t$22;
const t$30 = t$23;
const t$31 = t$24;
const t$32 = t$25;
const t$33 = t$26;
const t$34 = t$27;
const t$35 = t$21;
/** @type {number} */
let t$28;
try {
  t$28 = toListWithRemoving();
} catch {
  t$28 = -1;
}
/** @type {readonly number[]} */
const walked = Object.freeze([t$29, t$30, t$31, t$32, t$33, t$34, t$35, t$28]);
console.log(`walks ${show(walked)}`);
/** @type {number[]} */
const t = Object.freeze([3, 1, 2]).slice();

/**
 * @param {number} a
 * @param {number} z
 * @returns {number}
 */
function fn3(a, z) {
  listBuilderSet(t, 0, 99);
  return (a - z) | 0;
}

listBuilderSort(t, fn3);
/** @type {number[]} */
const u = Object.freeze([3, 1, 2]).slice();

/**
 * @param {number} a
 * @param {number} z
 * @returns {number}
 */
function fn4(a, z) {
  const last = listBuilderRemoveLast(u);
  u.push((last + 10) | 0);
  return (a - z) | 0;
}

listBuilderSort(u, fn4);
console.log(`same length ${show(t)} ${show(u)}`);
/** @type {number[]} */
const s = Object.freeze([3, 1, 2]).slice();

/**
 * @param {number} a
 * @param {number} z
 * @returns {number}
 */
function fn5(a, z) {
  if (s.length < 40) {
    s.push(0);
  }
  return (a - z) | 0;
}

listBuilderSort(s, fn5);
console.log(`sorted ${s.length}`);
