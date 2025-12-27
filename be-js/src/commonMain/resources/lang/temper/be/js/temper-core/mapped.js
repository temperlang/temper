import { pairConstructor } from "./pair.js";
import { bubble } from "./core.js";

/**
 * @typedef {import("./pair.js").Pair} Pair
 */

/**
 * @template K, V
 * @extends {Map<K, V>}
 */
export class FreezeMap extends Map {
  // TODO Don't worry to freeze? Or worry more by wrapping private map?
  // TODO Wrapping/Object.proxy presumably pays an extra cost when wrapped.
  clear() {
    throw new TypeError();
  }
  delete() {
    throw new TypeError();
  }
  set(key, value) {
    if (Object.isFrozen(this)) {
      // Crash only after frozen because constructor calls set.
      throw new TypeError();
    }
    return super.set(key, value);
  }
}

/**
 * @template K, V
 * @param {Pair<K, V>} entries
 * @returns {Map<K, V>}
 */
export const mapConstructor = (entries) => {
  return Object.freeze(new FreezeMap(entries));
}

/**
 * @template K, V
 * @param {Map<K, V>} entries
 * @returns {Map<K, V>}
 */
export const mapBuilderConstructor = (entries) => {
  return new Map();
}

/**
 * @template K, V
 * @param {Map<K, V>} builder
 * @param {K} key
 * @returns {V}
 */
export const mapBuilderRemove = (builder, key) => {
  const result = builder.get(key);
  if (builder.delete(key)) {
    return result;
  } else {
    bubble();
  }
}

/**
 * @template K, V
 * @param {Map<K, V>} builder
 * @param {K} key
 * @param {V} value
 */
export const mapBuilderSet = (builder, key, value) => {
  builder.set(key, value);
}

/**
 * @template K, V
 * @param {Map<K, V>} builder
 * @returns {FreezeMap<K, V>}
 */
export const mappedToMap = (mapped) => {
  if (mapped instanceof FreezeMap) {
    return mapped;
  }
  return Object.freeze(new FreezeMap(mapped));
}

/**
 * @template K, V
 * @param {Map<K, V>} mapped
 * @returns {Readonly<FreezeMap<K, V>>}
 */
export const mapBuilderToMap = (mapped) => {
  return Object.freeze(new FreezeMap(mapped));
}

/**
 * @template K, V
 * @param {Map<K, V>} mapped
 * @returns {Map<K, V>}
 */
export const mappedToMapBuilder = (mapped) => {
  return new Map(mapped);
}

////////////
// Mapped //
////////////

/**
 * @template K, V
 * @param {Map<K, V>} map
 * @returns {number}
 */
export const mappedLength = (map) => {
  return map.size;
}

/**
 * @template K, V
 * @param {Map<K, V>} map
 * @param {K} key
 * @returns {V}
 */
export const mappedGet = (map, key) => {
  const result = map.get(key);
  // TODO Under compiler-error-free Temper, could undefined values get set?
  // TODO Would Map<?, Void> be impossible to feed once we get checks in place?
  if (result === undefined) {
    bubble();
  }
  return result;
}

/**
 * @template K, V
 * @param {Map<K, V>} map
 * @param {K} key
 * @param {V} fallback
 * @returns {V}
 */
export const mappedGetOr = (map, key, fallback) => {
  return map.get(key) ?? fallback;
}

/**
 * @template K, V
 * @param {Map<K, V>} map
 * @param {K} key
 * @returns {boolean}
 */
export const mappedHas = (map, key) => {
  return map.has(key);
}

/**
 * @template K, V
 * @param {Map<K, V>} map
 * @returns {Readonly<K[]>}
 */
export const mappedKeys = (map) => {
  return Object.freeze(Array.from(map.keys()));
}

/**
 * @template K, V
 * @param {Map<K, V>} map
 * @returns {Readonly<V[]>}
 */
export const mappedValues = (map) => {
  return Object.freeze(Array.from(map.values()));
}

/**
 * @template K, V
 * @param {Map<K, V>} map
 * @returns {Readonly<Readonly<Pair<K, V>>[]>}
 */
export const mappedToList = (map) => {
  return Object.freeze(Array.from(map, ([key, value]) => pairConstructor(key, value)));
}

/**
 * @template K, V, R
 * @param {Map<K, V>} map
 * @param {(value: K) => R} func
 * @returns {Readonly<R[]>}
 */
export const mappedToListWith = (map, func) => {
  return Object.freeze(Array.from(map, ([key, value]) => func(key, value)));
}

/**
 * @template K, V, R
 * @param {Map<K, V>} map
 * @returns {Readonly<Pair<K, V>>[]}
 */
export const mappedToListBuilder = (map) => {
  return Object.freeze(Array.from(map, ([key, value]) => pairConstructor(key, value)));
}

/**
 * @template K, V, R
 * @param {Map<K, V>} map
 * @param {(value: K) => R} func
 * @returns {Readonly<R[]>}
 */
export const mappedToListBuilderWith = (map, func) => {
  return Object.freeze(Array.from(map, ([key, value]) => func(key, value)));
}

/**
 * @template K, V
 * @param {Map<K, V>} map
 * @param {(key: K, value: V) => void} func
 */
export const mappedForEach = (map, func) => {
  map.forEach((v, k) => func(k, v));
}
