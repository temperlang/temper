import {
  clampInt64,
  divInt64,
  divIntInt,
  int64Max,
  int64Min,
  int64ToInt32,
  int64ToInt32Unsafe,
  isBubble,
  modInt64,
  modIntInt,
  stringToInt32,
  stringToInt64,
} from "@temperlang/core";

/** @param {string} string */
function checkParse32(string) {
  /** @type {string} */
  let converted;
  try {
    converted = stringToInt32(string, 16).toString();
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    converted = "failed";
  }
  console.log(`Parse ${string} -> ${converted}`);
}

/** @param {string} string */
function checkParse64(string) {
  /** @type {string} */
  let converted;
  try {
    converted = stringToInt64(string, 16).toString();
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    converted = "failed";
  }
  console.log(`Parse ${string} -> ${converted}`);
}

console.log("Int32");
let one = 1;
one = one;
const big = Math.imul(2147483647, one);
const small = Math.imul(-2147483648, one);
console.log(`${big} + 1 == ${(big + 1) | 0}`);
console.log(`${small} - 1 == ${(small - 1) | 0}`);
console.log(`${big} * 4 == ${Math.imul(big, 4)}`);
console.log(`${small} * 4 == ${Math.imul(small, 4)}`);
console.log(`${(small + 1) | 0} * 4 == ${Math.imul((small + 1) | 0, 4)}`);
console.log(`${(small + 1) | 0} * ${big} == ${Math.imul((small + 1) | 0, big)}`);
console.log(`${small} / -1 == ${(small / -1) | 0}`);
console.log(`${small} % -1 == ${modIntInt(small, -1)}`);
console.log(`-(${small}) == ${-small | 0}`);
const three = Math.imul(3, one);
const five = Math.imul(5, one);
console.log(`${five} / ${three} == ${divIntInt(five, three)}`);
console.log(`${five} / -${three} == ${divIntInt(five, -three | 0)}`);
console.log(`-${five} / ${three} == ${divIntInt(-five | 0, three)}`);
console.log(`-${five} / -${three} == ${divIntInt(-five | 0, -three | 0)}`);
console.log(`${five} % ${three} == ${modIntInt(five, three)}`);
console.log(`${five} % -${three} == ${modIntInt(five, -three | 0)}`);
console.log(`-${five} % ${three} == ${modIntInt(-five | 0, three)}`);
console.log(`-${five} % -${three} == ${modIntInt(-five | 0, -three | 0)}`);
console.log("Int64");
const notSoBig = BigInt(big);
const notSoSmall = BigInt(small);
console.log(`${notSoBig.toString()} + 1 == ${clampInt64(notSoBig + 1n).toString()}`);
console.log(`${notSoSmall.toString()} - 1 == ${clampInt64(notSoSmall - 1n).toString()}`);
const big64 = clampInt64(9223372036854775807n * BigInt(one));
const small64 = clampInt64(-9223372036854775808n * BigInt(one));
console.log(`${big64.toString()} + 1 == ${clampInt64(big64 + 1n).toString()}`);
console.log(`${small64.toString()} - 1 == ${clampInt64(small64 - 1n).toString()}`);
console.log(`${small64.toString()} / -1 == ${clampInt64(small64 / -1n).toString()}`);
console.log(`${small64.toString()} % -1 == ${modInt64(small64, -1n).toString()}`);
console.log(`-(${small64.toString()}) == ${clampInt64(-small64).toString()}`);
const three64 = BigInt(three);
const five64 = BigInt(five);
console.log(`${five64.toString()} / ${three64.toString()} == ${divInt64(five64, three64).toString()}`);
console.log(`${five64.toString()} / -${three64.toString()} == ${divInt64(five64, clampInt64(-three64)).toString()}`);
console.log(`-${five64.toString()} / ${three64.toString()} == ${divInt64(clampInt64(-five64), three64).toString()}`);
console.log(`-${five64.toString()} / -${three64.toString()} == ${divInt64(clampInt64(-five64), clampInt64(-three64)).toString()}`);
console.log(`${five64.toString()} % ${three64.toString()} == ${modInt64(five64, three64).toString()}`);
console.log(`${five64.toString()} % -${three64.toString()} == ${modInt64(five64, clampInt64(-three64)).toString()}`);
console.log(`-${five64.toString()} % ${three64.toString()} == ${modInt64(clampInt64(-five64), three64).toString()}`);
console.log(`-${five64.toString()} % -${three64.toString()} == ${modInt64(clampInt64(-five64), clampInt64(-three64)).toString()}`);
checkParse32("7FFFFFFF");
checkParse32("-80000000");
checkParse32("80000000");
checkParse32("-80000001");
console.log(`Not so big: ${int64ToInt32Unsafe(notSoBig)}`);
/** @type {string} */
let t$0;
try {
  t$0 = int64ToInt32(big64).toString();
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  t$0 = "no";
}
console.log(`Actually big: ${t$0}`);
checkParse64("7FFFFFFFFFFFFFFF");
checkParse64("-8000000000000000");
checkParse64("8000000000000000");
checkParse64("-8000000000000001");
console.log(`${int64Min(small64, big64).toString()} < ${int64Max(small64, big64).toString()}`);
