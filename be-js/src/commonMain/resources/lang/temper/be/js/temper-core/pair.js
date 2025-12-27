
/**
 * @template K, V
 */
export class Pair {
  /** @type {K} */
  key;

  /** @type {V} */
  value;

  /**
   * @param {K} key
   * @param {V} value
   */
  constructor(key, value) {
    this.key = key;
    this.value = value;
  }

  /**
   * The key held by this pair.
   * @returns {K}
   */
  get key() {
    return this.key;
  }

  /**
   * The value held by this pair.
   * @returns {V}
   */
  get value() {
    return this.value;
  }

  /**
   * The first index is the same as the key.
   * @returns {K}
   */
  get [0]() {
    return this.key;
  }

  /**
   * The second index is the same as the value.
   * @returns {V}
   */
  get [1]() {
    return this.value;
  }

  /**
   * @returns {2}
   */
  get length() {
    return 2;
  }
}

/**
 * @template K, V
 * @param {K} key
 * @param {V} value
 * @returns {Readonly<Pair<K, V>>}
 */
export const pairConstructor = (key, value) => {
  return Object.freeze(new Pair(key, value));
}
