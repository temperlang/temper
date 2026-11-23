import { listedGet, listedSlice, type } from "@temperlang/core";

/**
 * @template T
 * @param {readonly T[]} ls
 * @returns {T | null}
 */
export function firstOrNull(ls) {
  if (!ls.length) {
    return null;
  } else {
    return listedGet(/** @type {T[]} */ (ls), 0);
  }
}

/**
 * @template T
 * @param {T} x
 * @param {T | null} [y]
 * @returns {T}
 */
export function withDefault(x, y) {
  y ??= x;
  return y;
}

/** @template T */
export class Slot extends type() {
  /** @type {T} */
  #value;

  /** @param {T} value */
  constructor(value) {
    super();
    this.#value = value;
  }

  /** @returns {T} */
  get value() {
    return this.#value;
  }

  /**
   * @param {boolean} keep
   * @returns {T | null}
   */
  kept(keep) {
    if (keep) {
      return this.#value;
    } else {
      return null;
    }
  }
}

export class Named extends type() {
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
}

/** @type {readonly (string | null)[]} */
const strings = Object.freeze([null, "b"]);
/** @type {readonly (string | null)[]} */
const noStrings = Object.freeze([]);
/** @type {readonly (number | null)[]} */
const ints = Object.freeze([null, 2]);
const a = firstOrNull(strings);
const b = firstOrNull(noStrings);
const c = firstOrNull(ints);
console.log(`${a == null} ${b == null} ${c == null}`);
console.log(firstOrNull(Object.freeze(["x"])) ?? "none");
console.log(`${firstOrNull(listedSlice(/** @type {(number | null)[]} */ (ints), 1, 2)) ?? -1} ${firstOrNull(Object.freeze([3])) ?? -1}`);
/** @type {Slot<string | null>} */
const slot = new Slot(null);
console.log(`${slot.kept(true) == null} ${slot.kept(false) == null}`);
console.log(new Slot("s").kept(true) ?? "none");
/** @type {readonly (Named | null)[]} */
const nameds = Object.freeze([null]);
/** @type {readonly Named[]} */
const noNameds = Object.freeze([]);
const n = firstOrNull(Object.freeze([new Named("n")]));
console.log(`${firstOrNull(nameds) == null} ${firstOrNull(noNameds) == null} ${(n == null ? null : n.name) ?? "none"}`);
/** @type {string | null} */
let t$0;
{
  const subject7 = firstOrNull(noNameds);
  t$0 = subject7 == null ? null : subject7.name;
}
console.log(t$0 ?? "none");
const none = null;
console.log(withDefault("a", none) ?? "null");
console.log(withDefault("a", "b") ?? "null");
console.log(withDefault("a") ?? "null");
console.log((withDefault(1, null) ?? -1).toString());
console.log(`${withDefault("c")} ${withDefault(4, 5)}`);
