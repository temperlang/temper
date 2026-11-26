import { stringGet } from "@temperlang/core";

/**
 * @param {string} s
 * @param {number} i
 * @returns {number}
 */
function codeAt(s, i) {
  return stringGet(s, i);
}

const text = "abc";
console.log(codeAt(text, 0).toString());
console.log("before");
console.log(codeAt(text, text.length).toString());
console.log("not reached");
