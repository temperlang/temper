import { type } from "@temperlang/core";

/**
 * @template T
 * @param {T} x
 * @returns {boolean}
 */
export function isNull(x) {
  return x == null;
}

/**
 * @template T
 * @param {T} x
 * @param {T} y
 * @returns {T}
 */
export function orElse(x, y) {
  {
    if (x == null) {
      return y;
    } else {
      return x;
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

console.log(`${isNull(null)} ${isNull("a")} ${isNull("a")}`);
console.log(`${isNull(null)} ${isNull(1)} ${isNull(1)}`);
console.log(`${isNull(null)} ${isNull(new Named("n"))}`);
console.log(orElse(null, "y") ?? "null");
console.log(`${orElse("x", "y")} ${orElse(null, 2) ?? -1}`);
