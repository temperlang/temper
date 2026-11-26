import { summary } from "./mine.js";

/**
 * @param {number} console_
 * @returns {number}
 */
function bump(console_) {
  return (console_ + 1) | 0;
}

console.log(summary().toString());
const s = "core's string";
console.log(s);
/** @type {readonly number[]} */
const xs = Object.freeze([1, 2]);
console.log(xs.length.toString());
console.log(bump(5).toString());
