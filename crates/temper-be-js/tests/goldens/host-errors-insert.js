import { bubble, isBubble, listBuilderAdd } from "@temperlang/core";

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

/** @returns {number[]} */
function built() {
  /** @type {number[]} */
  const lb = [];
  lb.push(1);
  return lb;
}

/**
 * @param {number[]} lb
 * @param {number} i
 * @returns {number}
 */
function insert(lb, i) {
  listBuilderAdd(lb, 2, at(i));
  return lb.length;
}

console.log("reading");
/** @type {number} */
let r;
try {
  r = insert(built(), 5);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  r = -1;
}
console.log(`added past the end ${r}`);
