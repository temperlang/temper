import {
  listBuilderAdd,
  listBuilderAddAll,
  listBuilderSplice,
  listBuilderToList,
  listedFilter,
  listedGet,
  modIntInt,
  stringCountBetween,
  stringFromCodePoints,
  stringNext,
} from "@temperlang/core";

const n = 200000;
/** @type {number[]} */
const source = [];
{
  let i = 0;
  while (i < n) {
    source.push(i);
    i = (i + 1) | 0;
  }
}
/** @type {readonly number[]} */
const values = listBuilderToList(source);
/** @type {number[]} */
const b = [];
listBuilderAddAll(b, /** @type {number[]} */ (values));
listBuilderAddAll(b, /** @type {number[]} */ (values), 0);
listBuilderAdd(b, -1, n);
listBuilderAddAll(b, /** @type {number[]} */ (values), n);
console.log(`${b.length} ${listedGet(b, 0)} ${listedGet(b, (n - 1) | 0)} ${listedGet(b, n)} ${listedGet(b, Math.imul(2, n))} ${listedGet(b, (Math.imul(2, n) + 1) | 0)} ${listedGet(b, Math.imul(3, n))}`);
/** @type {number[]} */
const twice = [];
listBuilderAddAll(twice, /** @type {number[]} */ (values));
listBuilderAddAll(twice, /** @type {number[]} */ (twice), 1);
console.log(`${twice.length} ${listedGet(twice, 0)} ${listedGet(twice, 1)} ${listedGet(twice, n)} ${listedGet(twice, (n + 1) | 0)}`);
/** @type {number[]} */
const s = [];
s.push(7);
s.push(8);
/** @type {readonly number[]} */
const removed = listBuilderSplice(s, 1, 0, values);
console.log(`${s.length} ${removed.length} ${listedGet(s, 0)} ${listedGet(s, 1)} ${listedGet(s, n)} ${listedGet(s, (n + 1) | 0)}`);
/** @type {number[]} */
const c = [];
c.push(7);
c.push(8);
listBuilderSplice(c, -1, 0, values);
listBuilderSplice(c, Math.imul(10, n), 0, values);
console.log(`${c.length} ${listedGet(c, 0)} ${listedGet(c, (n - 1) | 0)} ${listedGet(c, n)} ${listedGet(c, (n + 1) | 0)} ${listedGet(c, (n + 2) | 0)} ${listedGet(c, (Math.imul(2, n) + 1) | 0)}`);
/** @type {number[]} */
const own = [];
listBuilderAddAll(own, /** @type {number[]} */ (values));
/** @type {readonly number[]} */
const gone = listBuilderSplice(own, 0, 1, own);
console.log(`${own.length} ${gone.length} ${listedGet(own, 0)} ${listedGet(own, (n - 1) | 0)} ${listedGet(own, n)} ${listedGet(own, (Math.imul(2, n) - 2) | 0)}`);
/** @type {readonly number[]} */
const kept = listedFilter(/** @type {number[]} */ (values), (x) => x !== 5);
console.log(`${kept.length} ${listedGet(/** @type {number[]} */ (kept), 4)} ${listedGet(/** @type {number[]} */ (kept), 5)} ${listedGet(/** @type {number[]} */ (kept), (n - 2) | 0)}`);
/** @type {number[]} */
const codes = [];
{
  let i2 = 0;
  while (i2 < n) {
    codes.push((97 + modIntInt(i2, 3)) | 0);
    i2 = (i2 + 1) | 0;
  }
}
/** @type {string} */
let text;
try {
  text = stringFromCodePoints(/** @type {number[]} */ (listBuilderToList(codes)));
} catch {
  text = "";
}
const four = stringNext(text, stringNext(text, stringNext(text, stringNext(text, 0))));
console.log(`${stringCountBetween(text, 0, text.length)} ${text.substring(0, four)}`);
