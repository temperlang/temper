import { panic, type } from "@temperlang/core";

/**
 * @param {number} n
 * @returns {Shape}
 */
function shapeOf(n) {
  if (n > 1) {
    return new Line(n);
  } else {
    return new Dot(n);
  }
}

/**
 * @param {Shape} s
 * @returns {string}
 */
function named(s) {
  const name = s instanceof Dot ? "dot" : s instanceof Line ? "line" : panic();
  return name;
}

/**
 * @param {Shape} s
 * @param {Shape} t
 * @returns {string}
 */
function pair(s, t) {
  {
    if (s instanceof Dot) {
      if (t instanceof Dot) {
        return "dd";
      } else if (t instanceof Line) {
        return "dl";
      } else {
        panic();
      }
    } else if (s instanceof Line) {
      return "l";
    } else {
      panic();
    }
  }
}

/**
 * @param {number} n
 * @returns {number}
 */
function doubled(n) {
  return Math.imul(n, 2);
}

/**
 * @param {Shape} s
 * @param {boolean} early
 * @returns {number}
 */
function code(s, early) {
  if (early) {
    return s instanceof Dot ? 1 : s instanceof Line ? 2 : panic();
  }
  if (s.size > 1) {
    {
      if (s instanceof Dot) {
        return 10;
      } else if (s instanceof Line) {
        return 20;
      } else {
        panic();
      }
    }
  } else {
    return doubled(s instanceof Dot ? 3 : s instanceof Line ? 4 : panic());
  }
}

class Shape extends type() {
  /** @type {number} */
  size = /** @type {any} */ (undefined);
}

class Dot extends type(Shape) {
  /** @type {number} */
  #size;

  /** @param {number} size */
  constructor(size) {
    super();
    this.#size = size;
  }

  /** @returns {number} */
  get size() {
    return this.#size;
  }
}

class Line extends type(Shape) {
  /** @type {number} */
  #size;

  /** @param {number} size */
  constructor(size) {
    super();
    this.#size = size;
  }

  /** @returns {number} */
  get size() {
    return this.#size;
  }
}

class Namer extends type() {
  /**
   * @param {Shape} s
   * @returns {string}
   */
  name(s) {
    {
      if (s instanceof Dot) {
        return "a dot";
      } else if (s instanceof Line) {
        return "a line";
      } else {
        panic();
      }
    }
  }
}

const dot = shapeOf(1);
const line = shapeOf(2);
console.log(`${named(dot)} ${named(line)} ${pair(dot, line)} ${pair(line, dot)}`);
console.log(`${code(line, true)} ${code(dot, false)} ${code(line, false)} ${new Namer().name(line)}`);
