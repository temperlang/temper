/**
 * @param {number} n
 * @returns {number}
 */
export function shown(n) {
  shownCount = (shownCount + 1) | 0;
  return (n + base) | 0;
}

export let shownCount = 0;
export const base = 1;
export const checked = shown(2);
console.log(`shown ${shown(1)}`);

/** @param {number} value$ */
export function shownCount$set(value$) {
  shownCount = value$;
}
