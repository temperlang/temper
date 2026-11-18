import {
  isBubble,
  listedGet,
  listedJoin,
  listedMap,
  mapBuilderRemove,
  mapConstructor,
  mappedForEach,
  mappedGet,
  mappedGetOr,
  mappedToList,
  mappedToListWith,
  mappedToMap,
  pairConstructor,
} from "@temperlang/core";

/**
 * @template K
 * @template V
 * @param {Map<K, V>} mapped
 * @returns {readonly K[]}
 */
function listKeys(mapped) {
  return listedMap(/** @type {import("@temperlang/core/pair.js").Pair<K, V>[]} */ (mappedToList(mapped)), (entry) => entry.key);
}

/**
 * @param {number} code
 * @returns {string}
 */
function messageDefined(code) {
  return messages.has(code).toString();
}

/**
 * @param {readonly boolean[]} color
 * @returns {string}
 */
function colorToHex(color) {
  /**
   * @param {boolean} b
   * @returns {string}
   */
  function fn(b) {
    if (b) {
      return "FF";
    } else {
      return "00";
    }
  }
  const c = listedJoin(/** @type {boolean[]} */ (color), "", fn);
  return `#${c}`;
}

/**
 * @param {string} name
 * @param {Map<string, string>} map
 */
function printMap(name, map) {
  console.log(`--- ${name} ---`);
  /**
   * @param {string} key
   * @param {string} value
   */
  function fn(key, value) {
    console.log(`${key} = ${value}`);
  }
  mappedForEach(map, fn);
}

/**
 * @param {string} name
 * @param {Map<string, string>} map
 */
function printMapBuilder(name, map) {
  console.log(`--- ${name} ---`);
  /**
   * @param {string} key
   * @param {string} value
   */
  function fn(key, value) {
    console.log(`${key} = ${value}`);
  }
  mappedForEach(map, fn);
}

/** @type {Map<number, string>} */
const messages = mapConstructor(/** @type {any} */ (Object.freeze([pairConstructor(200, "OK"), pairConstructor(404, "Not Found"), pairConstructor(500, "Internal Server Error")])));
const message = mappedGet(messages, 200);
console.log(`Message: ${message}`);
console.log(`Has 200: ${messageDefined(200)}, 302: ${messageDefined(302)}`);
/** @type {string} */
let t$0;
try {
  t$0 = mappedGet(messages, 302);
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  t$0 = "Found";
}
console.log(`Fallback: ${t$0}`);
console.log(`Codes: ${listedJoin(/** @type {number[]} */ (listKeys(messages)), " ", (it) => it.toString())}`);
console.log(`Again: ${listedJoin(/** @type {number[]} */ (Object.freeze(Array.from(messages.keys()))), " ", (it) => it.toString())}`);
console.log(`Messages: ${listedJoin(/** @type {string[]} */ (Object.freeze(Array.from(messages.values()))), ", ", (it) => it)}`);
/** @type {readonly number[]} */
const groups = mappedToListWith(messages, (key, value) => (key / 100) | 0);
console.log(`Groups: ${listedJoin(/** @type {number[]} */ (groups), " ", (it) => it.toString())}`);
/** @type {Map<string, readonly string[]>} */
let t$1;
{
  /** @type {Map<string, readonly string[]>} */
  const builder2 = new Map();
  builder2.set("Honduras", Object.freeze(["El Salvador", "Guatemala", "Nicaragua"]));
  console.log(`From builder: ${listedJoin(/** @type {string[]} */ (mappedGet(builder2, "Honduras")), ", ", (it) => it)}`);
  console.log(`Key so far: ${listedGet(/** @type {string[]} */ (listKeys(builder2)), 0)}`);
  builder2.set("El Salvador", Object.freeze(["Guatemala", "Honduras"]));
  builder2.set("Nicaragua", Object.freeze(["Costa Rica", "Honduras"]));
  builder2.set("Panama", Object.freeze(["Colombia", "Costa Rica"]));
  try {
    mapBuilderRemove(builder2, "Honduras");
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
  }
  /** @type {readonly string[]} */
  const was = mapBuilderRemove(builder2, "El Salvador");
  console.log(`Was there: ${listedJoin(/** @type {string[]} */ (was), ", ", (it) => it)}`);
  /** @type {string} */
  let t$2;
  try {
    t$2 = listedGet(/** @type {string[]} */ (mapBuilderRemove(builder2, "El Salvador")), 0);
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    t$2 = "Gone";
  }
  console.log(t$2);
  t$1 = mappedToMap(builder2);
}
/** @type {Map<string, readonly string[]>} */
const neighbors = t$1;
console.log(`From built: ${listedJoin(/** @type {string[]} */ (mappedGet(neighbors, "Nicaragua")), ", ", (it) => it)}`);
/** @type {Map<number, number>} */
const mapOfNone = mapConstructor(/** @type {any} */ (Object.freeze([])));
/** @type {Map<string, string>} */
const mapOfOne = mapConstructor(/** @type {any} */ (Object.freeze([pairConstructor("jack", "all trades")])));
/** @type {Map<number, string>} */
const mapOfTwo = mapConstructor(/** @type {any} */ (Object.freeze([pairConstructor(1, "one"), pairConstructor(2, "electric boogaloo")])));
console.log(`mapOfNone: ${mapOfNone.size}`);
console.log(`mapOfOne: ${mapOfOne.size}`);
console.log(`mapOfTwo: ${mapOfTwo.size}`);
/** @type {Map<string, readonly boolean[]>} */
const eightColors = new Map();
eightColors.set("black", Object.freeze([false, false, false]));
eightColors.set("red", Object.freeze([true, false, false]));
eightColors.set("green", Object.freeze([false, true, false]));
eightColors.set("blue", Object.freeze([false, false, true]));
eightColors.set("yellow", Object.freeze([true, true, false]));
eightColors.set("cyan", Object.freeze([false, true, true]));
eightColors.set("purple", Object.freeze([true, false, true]));
eightColors.set("white", Object.freeze([true, true, true]));
console.log(`with 3 bits you can draw ${eightColors.size} colors.`);

/**
 * @param {string} color
 * @param {readonly boolean[]} bits
 */
function fn9(color, bits) {
  /** @type {readonly boolean[]} */
  const foundByGetOr = mappedGetOr(eightColors, color, Object.freeze([]));
  if (foundByGetOr.length !== 3) {
    console.log(`getOr("${color}") failed to find key`);
  }
  const want = colorToHex(bits);
  const got = colorToHex(foundByGetOr);
  if (want !== got) {
    console.log(`getOr("${color}") found ${got} but wanted ${want}`);
  }
}

mappedForEach(eightColors, fn9);
/** @type {Map<string, string>} */
let map = mapConstructor(/** @type {any} */ (Object.freeze([pairConstructor("key", "value"), pairConstructor("other key", "other value")])));
printMap("map", map);
/** @type {Map<string, string>} */
let builder = new Map(map);
builder.set("added key", "added value");
builder.set("key", "replaced value");
printMapBuilder("builder", builder);
printMap("map (same as above)", map);
/** @type {Map<string, string>} */
let newMap = mappedToMap(builder);
printMap("builder to map", newMap);
/** @type {Map<string, string>} */
const builderAsMapped = builder;
/** @type {Map<string, string>} */
const newMapFromMapped = mappedToMap(builderAsMapped);
mapBuilderRemove(builder, "other key");
builder.set("key", "double replaced value");
printMap("builder to map (same as above)", newMap);
printMap("builder to map via mapped (same as above)", newMapFromMapped);
builder.clear();
builder.set("something", "alone");
printMapBuilder("recently cleared", builder);
/** @type {Map<string, string>} */
let mayOrMayNotCopyMap = mappedToMap(map);
/** @type {Map<string, string>} */
let mapped = map;
/** @type {Map<string, string>} */
let mayOrMayNotCopyMapped = mappedToMap(mapped);
/** @type {Map<string, string>} */
let madeBy = new Map();
madeBy.set("temper", "temper systems");
printMapBuilder("made by", madeBy);
/** @type {Map<string, string>} */
let madeByAndJokes = new Map(madeBy);
madeByAndJokes.set("milk", "milking a cow");
printMapBuilder("made by (and a joke about milk)", madeByAndJokes);
printMapBuilder("made by (not changed from a change to a copy)", madeBy);
