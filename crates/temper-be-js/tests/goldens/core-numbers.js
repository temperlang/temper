import {
  clampInt64,
  eqFloat64,
  float64Near,
  listedReduceFromIndex,
} from "@temperlang/core";

/**
 * @param {number} index
 * @returns {number}
 */
function from_(index) {
  return listedReduceFromIndex(/** @type {number[]} */ (digits), 100, index, (s, n) => (Math.imul(s, 10) + n) | 0);
}

const one = 1;
const big = Math.imul(2147483647, one);
const small = Math.imul(-2147483648, one);
console.log(`${(big + 1) | 0} ${(small - 1) | 0} ${(one + 1) | 0} ${(one - 1) | 0} ${(small + 1) | 0} ${(big - 1) | 0}`);
const one64 = BigInt(one);
const big64 = clampInt64(9223372036854775807n * one64);
const small64 = clampInt64(clampInt64(-big64) - one64);
console.log(`${clampInt64(big64 + 1n).toString()} ${clampInt64(small64 - 1n).toString()} ${clampInt64(one64 - 1n).toString()}`);
const zero = (one - one) | 0;
console.log(`${Math.sign(big)} ${Math.sign(small)} ${Math.sign(zero)} ${Math.sign(one)} ${Math.sign((zero - one) | 0)} ${Math.sign((small + 1) | 0)}`);
/** @type {readonly number[]} */
const digits = Object.freeze([1, 2, 3]);
console.log(`${from_(0)} ${from_(1)} ${from_(3)} ${from_(7)}`);
const inf = 1e308 * 10.0;
const nan = inf - inf;
const ninf = 0.0 - inf;
console.log(`${float64Near(nan, nan)} ${float64Near(inf, inf)} ${float64Near(ninf, ninf)} ${float64Near(nan, 1.0)} ${float64Near(1.0, nan)}`);
console.log(`${float64Near(inf, 1.0)} ${float64Near(inf, ninf)} ${float64Near(inf, 1.0, 0.0, 0.0)} ${float64Near(inf, inf, 0.0, 0.0)} ${float64Near(inf, 1.0, 1.0, inf)} ${float64Near(1.0, ninf, 1.0, inf)}`);
console.log(`${float64Near(1.0, 1.5, nan, 1.0)} ${float64Near(1.0, 1.5, 1.0, nan)} ${float64Near(1.0, 1.0, nan, nan)} ${float64Near(-0.0, 0.0, 0.0, 0.0)}`);
console.log(`${float64Near(1.0, 1.0 + 1e-10)} ${float64Near(1.0, 1.1)} ${float64Near(1.0, 1.1, 0.2)} ${float64Near(0.0, 1e-300, 1e-9, 1e-12)}`);
console.log(`${eqFloat64(Math.max(nan, 1.0), nan)} ${eqFloat64(Math.max(1.0, nan), nan)} ${eqFloat64(Math.min(nan, 1.0), nan)} ${eqFloat64(Math.min(1.0, nan), nan)}`);
console.log(listedReduceFromIndex(/** @type {number[]} */ (digits), 0, -1, (s, n) => (s + 1) | 0).toString());
