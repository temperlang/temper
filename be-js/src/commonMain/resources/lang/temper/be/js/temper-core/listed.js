
// Implements extension method Listed::mapDropping
import {bubble} from "./core.js";

export const listedMapDropping = () => {
  throw new Error("TODO List::mapDropping");
}

/**
 * Implements extension method Listed::map
 * @template T
 * @template R
 * @param {T[]} ls
 * @param {(prev: T) => R} transform
 * @returns {Readonly<R[]>}
 */
export const listedMap = (ls, transform) => {
  let mapped = [];
  let { length } = ls;
  for (let i = 0; i < length; ++i) {
    mapped[i] = transform(ls[i]);
  }
  return Object.freeze(mapped);
}

/**
 * Implements extension method Listed::reduceFrom
 * @template T, R
 * @param {T[]} ls
 * @param {R} initial
 * @param {(prev: T, cur: R) => R}accumulate
 * @returns {R}
 */
export const listedReduceFrom = (ls, initial, accumulate) => {
  return ls.reduce(accumulate, initial);
}

/**
 * Implements extension method Listed::sort
 * @template T
 * @param {T[]} ls
 * @param {(lhs: T, rhs: T) => number} compare
 */
export const listedSorted = (ls, compare) => {
  return Object.freeze(ls.slice().sort(compare));
}

/**
 * Implements extension method Listed::toList
 * @template T
 * @param {T[] | Readonly<T[]>} ls
 * @returns {Readonly<T[]>}
 */
export const listedToList = (ls) => {
  if (Object.isFrozen(ls)) {
    return ls;
  } else {
    return listBuilderToList(ls);
  }
}


/**
 * Implements extension method ListBuilder::add
 * @template T
 * @param {T[]} ls
 * @param {T} newItem
 * @param {number | null} [at]
 */
export const listBuilderAdd = (ls, newItem, at) => {
  if (at == null) {
    // Technically, we could also use splice instead of push for this case.
    // Which is better might depend on minifiers and/or execution speed.
    ls.push(newItem);
  } else {
    if (at < 0 || at > ls.length) {
      bubble();
    }
    ls.splice(at, 0, newItem);
  }
}

/**
 * Implements extension method ListBuilder::addAll
 * @template T
 * @param {T[]} ls
 * @param {T[]} newItems
 * @param {number | null} [at]
 */
export const listBuilderAddAll = (ls, newItems, at) => {
  if (at == null) {
    ls.push(...newItems);
  } else {
    if (at < 0 || at > ls.length) {
      bubble();
    }
    ls.splice(at, 0, ...newItems);
  }
}

/**
 * Implements extension method Listed::filter
 * @template T
 * @param {T[]} ls
 * @param {(T) => boolean} predicate
 * @returns {Readonly<T[]>}
 */
export const listedFilter = (ls, predicate) => {
  let filtered = null;
  let nFiltered = 0; // Just past index of last element of ls filtered onto filtered
  let { length } = ls;
  for (let i = 0; i < length; ++i) {
    let element = ls[i];
    let ok = predicate(element);
    if (!ok) {
      if (!filtered) {
        filtered = [];
      }
      filtered.push(...ls.slice(nFiltered, i));
      nFiltered = i + 1;
    }
  }
  let fullyFiltered;
  if (filtered) {
    filtered.push(...ls.slice(nFiltered, length));
    fullyFiltered = filtered;
  } else {
    fullyFiltered = ls;
  }
  return Object.freeze(fullyFiltered);
}

/**
 * Implements extension method Listed::get
 * @template T
 * @param {T[]} ls
 * @param {number} i
 * @returns {T}
 */
export const listedGet = (ls, i) => {
  let { length } = ls;
  if (0 <= i && i < length) {
    return ls[i];
  }
  bubble();
}

/**
 * Implements extension method Listed::getOr
 * @template T
 * @param {T[]} ls
 * @param {number} i
 * @param {T} fallback
 * @returns {T}
 */
export const listedGetOr = (ls, i, fallback) => {
  let { length } = ls;
  return 0 <= i && i < length ? ls[i] : fallback;
}

/**
 * Implements extension method List::isEmpty
 * @template T
 * @param {T[]} ls
 * @returns {boolean}
 */
export const listIsEmpty = (ls) => {
  return !ls.length;
}

/**
 * Implements extension method Listed::join
 * @template T
 * @param {T[]} ls
 * @param {string} separator
 * @param {(T) => string} elementStringifier
 * @returns {string}
 */
export const listedJoin = (ls, separator, elementStringifier) => {
  let joined = "";
  let { length } = ls;
  for (let i = 0; i < length; ++i) {
    if (i) {
      joined += separator;
    }
    let element = ls[i];
    let stringifiedElement = elementStringifier(element);
    joined += stringifiedElement;
  }
  return joined;
}

/**
 * @template T
 * @param {T[]} ls
 */
// Implements extension method ListBuilder::clear
export const listBuilderClear = (ls) => {
  ls.length = 0;
}

/**
 * Implements extension method ListBuilder::removeLast
 * @template T
 * @param {T[]} ls
 * @returns {T}
 */
export const listBuilderRemoveLast = (ls) => {
  if (ls.length) {
    return ls.pop();
  } else {
    bubble();
  }
}

/**
 * Implements extension method ListBuilder::reverse
 * @template T
 * @param {T[]} ls
 */
export const listBuilderReverse = (ls) => {
  let { length } = ls;
  let lastIndex = length - 1;
  let mid = length >> 1;
  for (let i = 0; i < mid; ++i) {
    let j = lastIndex - i;
    let a = ls[i];
    ls[i] = ls[j];
    ls[j] = a;
  }
}

/**
 * Implements extension method ListBuilder::set
 * @template T
 * @param {T[]} ls
 * @param {number} i
 * @param {T} newValue
 */
export const listBuilderSet = (ls, i, newValue) => {
  if (0 <= i && i <= ls.length) {
    ls[i] = newValue;
  }
}

/**
 * Implements extension method ListBuilder::removeLast
 * @template T
 * @param {T[]} ls
 * @param {number} index
 * @param {number | null} [removeCount]
 * @param {T[] | null} [newValues]
 * @returns {Readonly<T[]>}
 */
export const listBuilderSplice = (ls, index, removeCount, newValues) => {
  // Missing count is all, but explicit undefined is 0, so give explicit length.
  if (removeCount == null) {
    removeCount = ls.length;
  }
  return Object.freeze(ls.splice(index, removeCount, ...(newValues || [])));
}

/**
 * Implements extension method ListBuilder::toList
 * @template T
 * @param {T[]} ls
 * @returns {Readonly<T[]>}
 */
export const listBuilderToList = (ls) => {
  return Object.freeze(ls.slice());
}

/**
 * Implements extension method Listed::slice
 * @template T
 * @param {T[]} ls
 * @param {number} startInclusive
 * @param {number} endExclusive
 * @returns {Readonly<T[]>}
 */
export const listedSlice = (ls, startInclusive, endExclusive) => {
  if (startInclusive < 0) {
    startInclusive = 0;
  }
  if (endExclusive < 0) {
    endExclusive = 0;
  }
  return Object.freeze(ls.slice(startInclusive, endExclusive));
}
