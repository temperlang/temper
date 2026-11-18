import {
  cmpFloat64,
  cmpString,
  listBuilderAdd,
  listBuilderAddAll,
  listBuilderClear,
  listBuilderRemoveLast,
  listBuilderReverse,
  listBuilderSet,
  listBuilderSplice,
  listBuilderToList,
  listedFilter,
  listedGet,
  listedGetOr,
  listedJoin,
  listedMap,
  listedSlice,
  listedSorted,
  listedToList,
  stringSplit,
} from "@temperlang/core";

/**
 * @template T
 * @param {readonly T[]} list
 */
function reportLength(list) {
  console.log(`Length: ${list.length}`);
}

/**
 * @param {string} string
 * @param {string} sep
 */
function reportSplit(string, sep) {
  /** @type {readonly string[]} */
  const strings = stringSplit(string, sep);
  const len = strings.length.toString();
  console.log(`Split ${listedJoin(/** @type {string[]} */ (strings), ", ", (x) => x)} - ${len}`);
}

/** @param {(arg0: readonly string[]) => void} callback */
function something(callback) {
  callback(Object.freeze(["Called", "with", "listed."]));
}

function runSomething() {
  /** @param {readonly string[]} words */
  function fn(words) {
    console.log(listedJoin(/** @type {string[]} */ (words), " ", (word) => word));
  }
  something(fn);
}

reportLength(Object.freeze([4, 5, 6]));
/** @type {readonly number[]} */
const ls = listedSorted(/** @type {number[]} */ (Object.freeze([5, 6, 7, 8, 9, 0, 1, 2, 3, 4])), (i, j) => (i - j) | 0);
console.log(`All  ${listedJoin(/** @type {number[]} */ (ls), ", ", (x) => x.toString())}`);
console.log(`Even ${listedJoin(/** @type {number[]} */ (listedFilter(/** @type {number[]} */ (ls), (x) => (x & 1) === 0)), ", ", (x) => x.toString())}`);
console.log(`Odd  ${listedJoin(/** @type {number[]} */ (listedFilter(/** @type {number[]} */ (ls), (x) => (x & 1) !== 0)), ", ", (x) => x.toString())}`);
console.log(`Neg  ${listedJoin(/** @type {number[]} */ (listedMap(/** @type {number[]} */ (ls), (x) => -x | 0)), ", ", (x) => x.toString())}`);
/** @type {readonly number[]} */
const listed = ls;
console.log(`Neg  ${listedJoin(/** @type {number[]} */ (listedMap(/** @type {number[]} */ (listed), (x) => -x | 0)), ", ", (x) => x.toString())}`);
/** @type {readonly number[]} */
const listedList = listedToList(listed);
console.log(`Listed list length is ${listedList.length}`);
console.log("Looping");
const n = ls.length;
{
  let i = 0;
  while (i < n) {
    console.log(`  ${listedGet(/** @type {number[]} */ (ls), i)}`);
    i = (i + 1) | 0;
  }
}
reportSplit("a ; b ; c", " ; ");
reportSplit("def", "");
reportSplit(" g  h i", " ");
reportSplit(" ", " ");
reportSplit("", " ");
reportSplit("", "");
reportSplit("🌍", "");

/**
 * @param {readonly string[]} list
 * @returns {string[]}
 */
function fn11(list) {
  /** @type {string[]} */
  const builder = list.slice();
  builder.sort((a, b) => Math.sign(cmpString(a, b)));
  return builder;
}

/** @type {string[][]} */
const triangleOfFooBar = listedMap(/** @type {(readonly string[])[]} */ (Object.freeze([Object.freeze([]), Object.freeze(["bar"]), Object.freeze(["bar", "baz"]), Object.freeze(["bar", "baz", "boo"]), Object.freeze(["bar", "baz", "boo", "far"]), Object.freeze(["bar", "baz", "boo", "far", "faz"]), Object.freeze(["far", "faz", "foo", "bar", "baz", "boo"])])), fn11).slice();
{
  let j = 0;
  while (j < triangleOfFooBar.length) {
    /** @type {string[]} */
    const row = listedGet(triangleOfFooBar, j);
    console.log(`Fwd  [${listedJoin(row, " ", (x) => x)}]`);
    listBuilderReverse(row);
    j = (j + 1) | 0;
  }
}
listBuilderReverse(triangleOfFooBar);
{
  let j2 = 0;
  while (j2 < triangleOfFooBar.length) {
    /** @type {string[]} */
    const row2 = listedGet(triangleOfFooBar, j2);
    console.log(`Back [${listedJoin(row2, " ", (x) => x)}]`);
    j2 = (j2 + 1) | 0;
  }
}
/** @type {string[]} */
const messageBuilder = [];
messageBuilder.push("hello");
listBuilderSet(messageBuilder, 0, "hi");
listBuilderAddAll(messageBuilder, /** @type {string[]} */ (Object.freeze(["there", "y'all"])));
listBuilderAdd(messageBuilder, "say", 0);
/** @type {string[]} */
const copyThat = messageBuilder.slice();
listBuilderAddAll(messageBuilder, /** @type {string[]} */ (Object.freeze(["more", "than"])), 1);
listBuilderAddAll(copyThat, /** @type {string[]} */ (messageBuilder));
console.log(listedJoin(messageBuilder, " ", (x) => x));
console.log(listedJoin(/** @type {string[]} */ (listedSorted(copyThat, (a, b) => Math.sign(cmpString(b, a)))), " ", (x) => x));
console.log(listedJoin(copyThat, " ", (x) => x));
const oldLast = listBuilderRemoveLast(copyThat);
console.log(`Removed ${oldLast} so ${listedJoin(copyThat, " ", (x) => x)}`);
/** @type {readonly string[]} */
const nextLast = listBuilderSplice(copyThat, (copyThat.length - 1) | 0, 1, null);
/** @type {readonly string[]} */
const oldWords = listBuilderSplice(copyThat, 4, 3, nextLast);
listBuilderSplice(copyThat, null, 0, oldWords);
listBuilderSplice(copyThat, copyThat.length, null, Object.freeze(["and", "the", "end"]));
console.log(listedJoin(copyThat, " ", (x) => x));
listBuilderSplice(copyThat, null, null, Object.freeze(["gone"]));
console.log(listedJoin(copyThat, " ", (x) => x));
/** @type {readonly string[]} */
const pastEnd = listBuilderSplice(copyThat, (copyThat.length + 5) | 0, 1, null);
listBuilderSplice(copyThat, -3, 0, Object.freeze(["first"]));
console.log(`${pastEnd.length} ${listedJoin(copyThat, " ", (x) => x)}`);
listBuilderClear(messageBuilder);
messageBuilder.push("Bye");
console.log(listedJoin(messageBuilder, " ", (x) => x));
{
  let i2 = -2;
  while (i2 <= 10) {
    console.log(`Slice(${i2}, ${(i2 + 2) | 0}): [${listedJoin(/** @type {number[]} */ (listedSlice(/** @type {number[]} */ (ls), i2, (i2 + 2) | 0)), ", ", (x) => x.toString())}]`);
    i2 = (i2 + 1) | 0;
  }
}
console.log(`Outside fallback: ${listedGetOr(/** @type {number[]} */ (ls), ls.length, 123)}`);
{
  /**
   * @param {boolean} b
   * @returns {string}
   */
  function booleanToString(b) {
    return b.toString();
  }
  /** @type {readonly boolean[]} */
  const ls2 = Object.freeze([true, false]);
  const joined = listedJoin(/** @type {boolean[]} */ (ls2), ", ", booleanToString);
  /** @type {readonly boolean[]} */
  const inverted = listedMap(/** @type {boolean[]} */ (ls2), (b) => !b);
  /**
   * @param {boolean} b
   * @returns {string}
   */
  function fn23(b) {
    if (b) {
      return "not false";
    } else {
      return "not true";
    }
  }
  const joinedAndInverted = listedJoin(/** @type {boolean[]} */ (inverted), ", ", fn23);
  console.log(`list = [${joined}]`);
  console.log(`list (each inverted) = [${joinedAndInverted}]`);
  /** @type {readonly boolean[]} */
  const onlyTrue = listedFilter(/** @type {boolean[]} */ (ls2), (b) => b);
  /** @type {readonly boolean[]} */
  const onlyFalse = listedFilter(/** @type {boolean[]} */ (ls2), (b) => !b);
  console.log(`list (== true only) = [${listedJoin(/** @type {boolean[]} */ (onlyTrue), ", ", booleanToString)}]`);
  console.log(`list (== false only) = [${listedJoin(/** @type {boolean[]} */ (onlyFalse), ", ", booleanToString)}]`);
}
{
  /** @type {readonly number[]} */
  const sizes = Object.freeze([0.25, 3.0]);
  /**
   * @param {number} f
   * @returns {string}
   */
  function fn12(f) {
    if (cmpFloat64(f, 1.0) < 0) {
      return "small";
    } else {
      return "big";
    }
  }
  /** @type {readonly string[]} */
  const labels = listedMap(/** @type {number[]} */ (sizes), fn12);
  console.log(`sizes = [${listedJoin(/** @type {string[]} */ (labels), ", ", (s) => s)}]`);
}
runSomething();
{
  /** @type {string[]} */
  const aListBuilder = [];
  aListBuilder.push("first");
  /** @type {readonly string[]} */
  const builtFirst = listBuilderToList(aListBuilder);
  aListBuilder.push("second");
  /** @type {readonly string[]} */
  const builtSecond = listedToList(aListBuilder);
  aListBuilder.push("third");
  console.log(`builtFirst has ${listedJoin(/** @type {string[]} */ (builtFirst), ", ", (s) => s)}`);
  console.log(`builtSecond has ${listedJoin(/** @type {string[]} */ (builtSecond), ", ", (s) => s)}`);
}
