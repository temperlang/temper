import { sum } from "modules";
import { Ratio, ratioOf } from "modules/core";
import { base, twice } from "modules/helpers";
import { Spot } from "modules/state";
import { Builtins, Checked, lambda, native } from "renamed";

/**
 * @param {number} n
 * @returns {number}
 */
export function both(n) {
  return (((sum(n) + twice(n)) | 0) + base) | 0;
}

console.log(`both ${both(2)}`);
const third = new Ratio(1, 3);
/** @type {boolean} */
let t$0;
{
  const left = ratioOf(2, 6);
  t$0 = left == null ? false : left.eq(third);
}
/** @type {boolean} */
let t$1;
{
  const left2 = ratioOf(1, 0);
  t$1 = left2 == null ? false : left2.eq(third);
}
console.log(`ratio ${third.eq(new Ratio(3, 9))} ${t$0} ${t$1}`);
const checked = new Checked(lambda(native));
console.log(`renamed ${checked.delete_()} ${checked.local} ${checked.end}`);
const b = new Builtins(4);
console.log(`builtins ${b.hash} ${b.get(1)} ${b.set(3)} ${b.map(5)} ${b.format()} ${b.int} ${b.later()}`);
console.log(b.print());
/** @type {Spot} */
let t$2;
{
  const spread = new Spot(3, 4);
  t$2 = new Spot(9, spread.y);
}
const far = t$2;
console.log(`spot far ${far.x} ${far.y}`);
