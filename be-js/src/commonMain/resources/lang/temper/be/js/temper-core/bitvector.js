
/**
 * @param {number} byte
 * @returns {string}
 */
const byteToString = (byte) => {
  return byte.toString(2).padStart(8, '0');
};

/**
 * @param {number} byte
 * @returns {boolean[]}
 */
const byteToBitArray = (byte) => {
  const ret = [];
  for (let i = 0; i < 8; i++) {
    ret.push((byte & 1) !== 0);
    byte >>= 1;
  }
  return ret;
};

export class DenseBitVector {
  /**
   * @type {Uint8Array}
   */
  values;

  constructor() {
    this.values = new Uint8Array(8);
  }

  /**
   * @param {number} index
   * @returns {boolean}
   */
  get(index) {
    const number = this.values[index >> 3] || 0;
    return (number & (1 << (index & 7))) !== 0;
  }

  /**
   * @param {number} index
   * @param {boolean} newBitValue
   */
  set(index, newBitValue) {
    let values = this.values;
    const len = values.byteLength;
    const key = index >> 3;
    const shift = index & 7;
    if (len <= key) {
      const next = new Uint8Array(len << 1);
      next.set(values);
      next[key] = next[key] & ~(1 << shift) | (newBitValue << shift);
      this.values = next;
    } else {
      values[key] = values[key] & ~(1 << shift) | (newBitValue << shift);
    }
  }

  /**
   * @returns {string}
   */
  toString() {
    return `0b${Array.prototype.map.call(this.values, byteToString).join('')}`;
  }

  /**
   * @returns {boolean[]}
   */
  toJSON() {
    return Array.prototype.flatMap.call(this.values, byteToBitArray);
  }
};

/**
 * Implements extension method DenseBitVector::constructor
 * @param {number} capacity
 * @returns {DenseBitVector}
 */
export const denseBitVectorConstructor = (capacity) => {
  return new DenseBitVector();
};

/**
 * Implements extension method DenseBitVector::get
 * @param {DenseBitVector} denseBitVector
 * @param {number} index
 * @returns {boolean}
 */
export const denseBitVectorGet = (denseBitVector, index) => {
  return denseBitVector.get(index);
};

/**
 * Implements extension method DenseBitVector::set
 * @param {DenseBitVector} denseBitVector
 * @param {number} index
 * @param {boolean} newBitValue
 */
export const denseBitVectorSet = (denseBitVector, index, newBitValue) => {
  denseBitVector.set(index, newBitValue);
};
