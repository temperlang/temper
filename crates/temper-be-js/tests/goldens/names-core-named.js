import { type } from "@temperlang/core";
import { greet, share } from "./core.js";

/**
 * @param {number} core
 * @returns {number}
 */
function twice(core) {
  return Math.imul(core, 2);
}

class String_ extends type() {
  /** @type {number} */
  #n;

  /** @param {number} n */
  constructor(n) {
    super();
    this.#n = n;
  }

  /** @returns {number} */
  get n() {
    return this.#n;
  }
}

const core = greet("a module named core");
console.log(core);
console.log(`shares ${share(7, 2)} ${share(1, 0)}`);
let builtins = 1;
builtins = (builtins + 1) | 0;
console.log(`builtins is ${builtins}`);
const s = "core's String";
const mine = new String_(3);
console.log(`${s}, and mine holds ${mine.n}`);
console.log(twice(21).toString());
