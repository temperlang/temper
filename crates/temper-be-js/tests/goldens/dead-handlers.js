/**
 * @param {number} n
 * @returns {number}
 */
function caught(n) {
  return (n + 1) | 0;
}

/**
 * @param {number} n
 * @returns {number}
 */
function orElse(n) {
  return (n + 2) | 0;
}

console.log(caught(1).toString());
console.log(orElse(1).toString());
console.log("caught");
console.log("or else");
