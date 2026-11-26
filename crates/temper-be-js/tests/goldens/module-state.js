import { listedGet, type } from "@temperlang/core";

/** @returns {number} */
function bump() {
  count = (count + 1) | 0;
  total = (total + count) | 0;
  return count;
}

export function tick() {
  ticks = (ticks + 1) | 0;
}

/** @returns {string} */
export function report() {
  return `${greeting} ${bump()} of ${limit} ${total}`;
}

/** @type {Palette} */
let Palette$warm;

export class Palette extends type() {
  /** @type {string} */
  #name;

  /** @returns {Palette} */
  static get warm() {
    return Palette$warm;
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
   * @param {string} n
   * @returns {Palette}
   */
  static named(n) {
    return new Palette(listedGet(/** @type {string[]} */ (prefixes), 0) + n);
  }
}

export const greeting = "hi";
let count = 0;
const limit = 3;
export let total = 10;
export let ticks = 0;
let local = 1;
/** @type {readonly string[]} */
const prefixes = Object.freeze(["p-"]);
Palette$warm = Palette.named("warm");
export const favorite = Palette.warm;
local = (local + limit) | 0;
console.log(report());
console.log(report());
console.log(`local ${local}`);
console.log(`${Palette.warm.name} ${favorite.name}`);
