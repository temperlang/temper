import {
  bubble,
  dequeConstructor,
  dequeRemoveFirst,
  isBubble,
} from "@temperlang/core";

/**
 * @param {number} i
 * @returns {number}
 */
function at(i) {
  if (i === 99) {
    bubble();
  }
  return i;
}

/**
 * @param {number[]} d
 * @param {number} i
 * @returns {number}
 */
function removeFirst(d, i) {
  at(i);
  return dequeRemoveFirst(d);
}

console.log("reading");
/** @type {number} */
let r;
try {
  r = removeFirst(dequeConstructor(), 0);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  r = -1;
}
console.log(`removed from empty ${r}`);
