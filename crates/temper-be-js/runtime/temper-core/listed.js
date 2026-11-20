
/**
 * Implements extension method core.type Listed.map()
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
 * Implements extension method core.type Listed.reduceFrom()
 * @template T, R
 * @param {readonly T[]} ls
 * @param {R} initial
 * @param {(prev: R, cur: T) => R} accumulate
 * @returns {R}
 */
export const listedReduceFrom = (ls, initial, accumulate) => {
  return ls.reduce(accumulate, initial);
}

/**
 * Implements extension method core.type Listed.sort()
 * @template T
 * @param {T[]} ls
 * @param {(lhs: T, rhs: T) => number} compare
 */
export const listedSorted = (ls, compare) => {
  return Object.freeze(ls.slice().sort(compare));
}

/**
 * Implements extension method core.type Listed.toList()
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
 * Implements extension method core.type ListBuilder.add()
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
 * Pushes `items[from..to]` onto `ls` one at a time, as `push(...items)`
 * overflows the stack past about 120,000 arguments.
 * @template T
 * @param {T[]} ls
 * @param {readonly T[]} items
 * @param {number} [from]
 * @param {number} [to]
 */
const pushAll = (ls, items, from = 0, to = items.length) => {
  for (let i = from; i < to; ++i) {
    ls.push(items[i]);
  }
}

/**
 * Inserts `items` into `ls` at `at`, with `ls` itself copied first.
 * @template T
 * @param {T[]} ls
 * @param {number} at
 * @param {readonly T[]} items
 */
const insertAll = (ls, at, items) => {
  if (at === ls.length) {
    pushAll(ls, items);
  } else if (items.length) {
    const inserted = items === ls ? ls.slice() : items;
    const tail = ls.splice(at);
    pushAll(ls, inserted);
    pushAll(ls, tail);
  }
}

/**
 * Implements extension method core.type ListBuilder.addAll()
 * @template T
 * @param {T[]} ls
 * @param {T[]} newItems
 * @param {number | null} [at]
 */
export const listBuilderAddAll = (ls, newItems, at) => {
  if (at == null) {
    pushAll(ls, newItems);
  } else {
    if (at < 0 || at > ls.length) {
      bubble();
    }
    insertAll(ls, at, newItems);
  }
}

/**
 * Implements extension method core.type Listed.filter()
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
      pushAll(filtered, ls, nFiltered, i);
      nFiltered = i + 1;
    }
  }
  let fullyFiltered;
  if (filtered) {
    pushAll(filtered, ls, nFiltered, length);
    fullyFiltered = filtered;
  } else {
    fullyFiltered = ls;
  }
  return Object.freeze(fullyFiltered);
}

/**
 * Implements extension method core.type Listed.get()
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
 * Implements extension method core.type Listed.getOr()
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
 * Implements extension method core.type List.isEmpty()
 * @template T
 * @param {T[]} ls
 * @returns {boolean}
 */
export const listIsEmpty = (ls) => {
  return !ls.length;
}

/**
 * Implements extension method core.type Listed.join()
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
// Implements extension method core.type ListBuilder.clear()
export const listBuilderClear = (ls) => {
  ls.length = 0;
}

/**
 * Implements extension method core.type ListBuilder.removeLast()
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
 * Implements extension method core.type ListBuilder.reverse()
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
 * Implements extension method core.type ListBuilder.set()
 * @template T
 * @param {T[]} ls
 * @param {number} i
 * @param {T} newValue
 */
export const listBuilderSet = (ls, i, newValue) => {
  if (0 <= i && i < ls.length) {
    ls[i] = newValue;
  }
}

/**
 * Implements extension method core.type ListBuilder.removeLast()
 * @template T
 * @param {T[]} ls
 * @param {number | null} [index]
 * @param {number | null} [removeCount]
 * @param {readonly T[] | null} [newValues]
 * @returns {Readonly<T[]>}
 */
export const listBuilderSplice = (ls, index, removeCount, newValues) => {
  // Missing count is all, but explicit undefined is 0, so give explicit length.
  if (removeCount == null) {
    removeCount = ls.length;
  }
  // Where splice starts, which the new values go in at.
  const { length } = ls;
  let start = Math.trunc(index ?? 0);
  start = start < 0 ? Math.max(length + start, 0) : Math.min(start, length);
  // Copied first if it's the builder itself, as splice changes it.
  const values = newValues === ls ? ls.slice() : newValues;
  const removed = ls.splice(start, removeCount);
  if (values) {
    insertAll(ls, start, values);
  }
  return Object.freeze(removed);
}

/**
 * Implements extension method core.type ListBuilder.toList()
 * @template T
 * @param {T[]} ls
 * @returns {Readonly<T[]>}
 */
export const listBuilderToList = (ls) => {
  return Object.freeze(ls.slice());
}

/**
 * Implements extension method core.type Listed.slice()
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
