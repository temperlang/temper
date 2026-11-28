import { type } from "@temperlang/core";
import { Ratio, ratioOf, share } from "./core.js";
import { base, bump, calls, intDoubled, twice } from "./helpers.js";
import { greet } from "./helpers/deep.js";
import { describe } from "./machine.js";
import { Spot } from "./state.js";

/**
 * @param {number} n
 * @returns {number}
 */
export function sum(n) {
  return (((twice(n) + base) | 0) + base2) | 0;
}

class Engine extends type() {
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

const calls2 = calls;
const base2 = 1;
console.log(greet("modules"));
console.log(`sum ${sum(3)}`);
console.log(`share ${share(7, 2)} ${intDoubled(21)}`);
console.log(`${new Engine("own").name}, ${describe()}`);
bump();
bump();
console.log(`calls ${calls2}`);
console.log(`calls now ${bump()}`);
const half = new Ratio(1, 2);
/** @type {boolean} */
let t$0;
{
  const left = ratioOf(3, 6);
  t$0 = left == null ? false : left.eq(half);
}
/** @type {boolean} */
let t$1;
{
  const left2 = half;
  const right = ratioOf(1, 0);
  t$1 = right == null ? false : left2.eq(right);
}
console.log(`${half.eq(new Ratio(2, 4))} ${t$0} ${!t$1}`);
/** @type {Spot} */
let t$2;
{
  const spread = new Spot(1, 2);
  t$2 = new Spot(spread.x, 5);
}
const moved = t$2;
console.log(`spot ${moved.x} ${moved.y}`);
