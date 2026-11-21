import * as _connected from "./_connected.js";
import { isBubble, panic } from "@temperlang/core";

/**
 * @param {number} n
 * @returns {number}
 */
export function hostFails(n) {
  return _connected.hostFails(n);
}

console.log("before");
/** @type {number} */
export let h;
try {
  h = hostFails(1);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  h = panic("panic", e$);
}
console.log(`after ${h}`);
