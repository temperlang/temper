import { listedGet, type } from "@temperlang/core";

class C extends type() {
  /**
   * @param {number} x
   * @returns {number}
   */
  #helper(x) {
    return (x + 1) | 0;
  }

  /**
   * @param {number} x
   * @returns {number}
   */
  method1(x) {
    return this.#helper(Math.imul(x, 2));
  }

  /**
   * @param {number} x
   * @returns {number}
   */
  method2(x) {
    return this.#helper(Math.imul(x, 3));
  }
}

class Money extends type() {
  /** @type {number} */
  #cents;
  /** @type {number} */
  #spent;

  /** @param {number} cents */
  constructor(cents) {
    super();
    this.#cents = cents;
    this.#spent = 0;
  }

  /** @returns {number} */
  #whole() {
    return (this.#cents / 100) | 0;
  }

  /** @returns {number} */
  get #rounded() {
    return (((this.#cents + 50) | 0) / 100) | 0;
  }

  /**
   * @param {Money} other
   * @returns {Money}
   */
  plus(other) {
    return new Money((this.#cents + other.#cents) | 0);
  }

  /**
   * @param {Money} other
   * @returns {boolean}
   */
  sameWhole(other) {
    return this.#whole() === other.#whole();
  }

  /**
   * @param {Money} other
   * @returns {boolean}
   */
  sameRounded(other) {
    return this.#rounded === other.#rounded;
  }

  /** @param {Money} other */
  spendFrom(other) {
    other.#spent = (other.#spent + 1) | 0;
  }

  /** @returns {number} */
  get timesSpent() {
    return this.#spent;
  }

  /** @returns {string} */
  toString() {
    return `${this.#cents} cents`;
  }
}

class Sink extends type() {
  /** @type {() => number} */
  end = /** @type {any} */ (undefined);
}

class TenSink extends type(Sink) {
  /** @returns {number} */
  end() {
    return 10;
  }
}

class Note extends type() {
  /** @type {string} */
  #body;

  /** @param {string} body */
  constructor(body) {
    super();
    this.#body = body;
  }

  /** @returns {string} */
  get body() {
    return this.#body;
  }

  /**
   * @param {string} more
   * @returns {Note}
   */
  text(more) {
    return new Note(this.#body + more);
  }
}

class Label extends type() {
  /** @type {number} */
  #end;

  /** @param {number} end */
  constructor(end) {
    super();
    this.#end = end;
  }

  /** @returns {string} */
  get text() {
    return "label";
  }

  /**
   * @param {Sink} sink
   * @returns {number}
   */
  close(sink) {
    return (sink.end() + this.#end) | 0;
  }

  /**
   * @param {string} more
   * @returns {string}
   */
  noted(more) {
    return new Note(this.text).text(more).body;
  }
}

class Tally extends type() {
  /** @type {number} */
  #depth;
  /** @type {number} */
  #size;

  /**
   * @param {{ size: number, parent: Tally | null, children: readonly Tally[] }} props
   * @returns {Tally}
   */
  static new(props) {
    return new Tally(props.size, props.parent, props.children);
  }

  /**
   * @param {number} size
   * @param {Tally | null} parent
   * @param {readonly Tally[]} children
   */
  constructor(size, parent, children) {
    super();
    this.#depth = parent == null ? 0 : (parent.#depth + 1) | 0;
    let sum = size;
    {
      let i = 0;
      while (i < children.length) {
        sum = (sum + listedGet(/** @type {Tally[]} */ (children), i).#size) | 0;
        i = (i + 1) | 0;
      }
    }
    this.#size = (sum + this.#depth) | 0;
  }

  /** @returns {number} */
  get depth() {
    return this.#depth;
  }

  /** @returns {number} */
  get total() {
    return this.#size;
  }
}

const c = new C();
console.log(`method1(10)=${c.method1(10)}`);
console.log(`method2(10)=${c.method2(10)}`);
const a = new Money(150);
const b = new Money(175);
console.log(`plus: ${a.plus(b).toString()}`);
console.log(`same whole: ${a.sameWhole(b)}`);
console.log(`same rounded: ${a.sameRounded(b)}`);
a.spendFrom(b);
a.spendFrom(b);
console.log(`b spent: ${b.timesSpent}`);
const label = new Label(1);
console.log(`close: ${label.close(new TenSink())}`);
console.log(`noted: ${label.noted("!")}`);
const leaf = new Tally(2, null, Object.freeze([]));
const mid = new Tally(3, leaf, Object.freeze([leaf, leaf]));
const top = new Tally(1, mid, Object.freeze([mid, leaf]));
console.log(`depths: ${leaf.depth} ${mid.depth} ${top.depth}`);
console.log(`totals: ${leaf.total} ${mid.total} ${top.total}`);
