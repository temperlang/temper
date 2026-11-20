import {
  float64ToInt32Unsafe,
  float64ToString,
  listBuilderSort,
  listedGet,
  listedJoin,
  listedSorted,
  type,
} from "@temperlang/core";

/** @param {readonly number[]} ints */
function mungeInts(ints) {
  /** @type {number[]} */
  const builder = ints.slice();
  listBuilderSort(builder, (a, b) => (a - b) | 0);
  console.log(listedJoin(/** @type {number[]} */ (listedSorted(/** @type {number[]} */ (ints), (a2, b2) => (a2 - b2) | 0)), ", ", (a3) => a3.toString()));
  console.log(listedJoin(builder, ", ", (a4) => a4.toString()));
}

/** @param {readonly number[]} floats */
function mungeFloats(floats) {
  /** @type {number[]} */
  const builder = floats.slice();
  /**
   * @param {number} a
   * @param {number} b
   * @returns {number}
   */
  function compareFloats(a, b) {
    return float64ToInt32Unsafe(Math.sign(a - b));
  }
  listBuilderSort(builder, compareFloats);
  console.log(listedJoin(/** @type {number[]} */ (listedSorted(/** @type {number[]} */ (floats), compareFloats)), ", ", (a2) => float64ToString(a2)));
  console.log(listedJoin(builder, ", ", (a3) => float64ToString(a3)));
}

class Entry extends type() {
  /** @type {number} */
  #id;
  /** @type {number} */
  #group;

  /**
   * @param {{ id: number, group: number }} props
   * @returns {Entry}
   */
  static new(props) {
    return new Entry(props.id, props.group);
  }

  /**
   * @param {number} id
   * @param {number} group
   */
  constructor(id, group) {
    super();
    this.#id = id;
    this.#group = group;
  }

  /** @returns {number} */
  get id() {
    return this.#id;
  }

  /** @returns {number} */
  get group() {
    return this.#group;
  }

  /** @returns {string} */
  toString() {
    return `{ id: ${this.#id}, group: ${this.#group} }`;
  }
}

/** @type {Entry[]} */
const entries = Object.freeze([new Entry(0, 0), new Entry(1, 1), new Entry(2, 2), new Entry(3, 0), new Entry(4, 1), new Entry(5, 2), new Entry(6, 0), new Entry(7, 1), new Entry(8, 2)]).slice();
/** @type {readonly Entry[]} */
const sortedEntries = listedSorted(entries, (a, b) => (a.group - b.group) | 0);
console.log(listedJoin(/** @type {Entry[]} */ (sortedEntries), "\n", (a) => a.toString()));
console.log(listedGet(entries, 1).toString());
listBuilderSort(entries, (a, b) => (a.group - b.group) | 0);
console.log(listedJoin(entries, "\n", (a) => a.toString()));
mungeInts(Object.freeze([2, 1]));
mungeFloats(Object.freeze([3.4, 1.2]));
