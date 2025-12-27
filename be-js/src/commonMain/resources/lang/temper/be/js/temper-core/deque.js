
// Implements extension method Deque::add
import {bubble} from "./core.js";

const DEQUE_NTAKEN = Symbol("Deque::nTaken");

/**
 * Implements extension method Deque::constructor
 * @template T
 * @returns {T[]}
 */
export const dequeConstructor = () => {
  let deque = [];
  Object.defineProperty(deque, DEQUE_NTAKEN, { value: 0, writable: true });
  return deque;
};

/**
 * Implements extension method Deque::add
 * @template T
 * @param {T[]} deque
 * @param {T} element
 */
export const dequeAdd = (deque, element) => {
  deque.push(element);
};

/**
 * Implements extension method Deque::isEmpty
 * @template T
 * @param {T[]} deque
 * @returns {boolean}
 */
export const dequeIsEmpty = (deque) => {
  return deque.length === (deque[DEQUE_NTAKEN] || 0);
};

/**
 * Implements extension method Deque::removeFirst
 * @template T
 * @param {T[]} deque
 * @returns {T}
 */
export const dequeRemoveFirst = (deque) => {
  // https://gist.github.com/mikesamuel/444258e7005e8fc9534d9cf274b1df58
  let nTaken = deque[DEQUE_NTAKEN];
  let length = deque.length;
  if (length === nTaken) {
    deque[DEQUE_NTAKEN] = 0;
    deque.length = 0;
    bubble();
  }
  let item = deque[nTaken];
  let nShiftThreshold = (length / 2) | 0;
  if (nShiftThreshold < 32) {
    nShiftThreshold = 32;
  }
  if (nTaken >= nShiftThreshold) {
    deque.splice(0, nTaken + 1);
    deque[DEQUE_NTAKEN] = 0;
  } else {
    deque[nTaken] = undefined;
    deque[DEQUE_NTAKEN] = nTaken + 1;
  }
  return item;
};

