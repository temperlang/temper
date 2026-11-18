import { bubble, isBubble, listBuilderRemoveLast } from "@temperlang/core";

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
 * @param {number[]} lb
 * @param {number} i
 * @returns {number}
 */
function removeLast(lb, i) {
  at(i);
  return listBuilderRemoveLast(lb);
}

console.log("reading");
/** @type {number} */
let r;
try {
  r = removeLast([], 0);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  r = -1;
}
console.log(`removed from empty ${r}`);
