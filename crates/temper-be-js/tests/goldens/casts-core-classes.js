import {
  denseBitVectorConstructor,
  dequeConstructor,
  mapConstructor,
  pairConstructor,
  requireInstanceOf,
  type,
} from "@temperlang/core";

/**
 * @param {unknown} x
 * @returns {string}
 */
function describe(x) {
  if (x instanceof Thing) {
    return "thing";
  }
  try {
    requireInstanceOf(x, Thing);
    return "thing, as";
  } catch {
    return "other";
  }
}

/**
 * @param {unknown} x
 * @returns {boolean}
 */
function isThing(x) {
  return x instanceof Thing;
}

class Thing extends type() {}

const bits = denseBitVectorConstructor(8);
/** @type {readonly number[]} */
const ints = Object.freeze([1, 2]);
console.log(describe(new Thing()));
console.log(describe(pairConstructor("a", 1)));
console.log(describe(bits));
console.log(describe("s"));
console.log(describe(1));
console.log(describe(ints));
console.log(describe(mapConstructor(/** @type {any} */ (Object.freeze([pairConstructor("m", 2)])))));
console.log(describe([]));
console.log(describe(dequeConstructor()));
console.log(isThing(new Thing()).toString());
console.log(isThing(bits).toString());
