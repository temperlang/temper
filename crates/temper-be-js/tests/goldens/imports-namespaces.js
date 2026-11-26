import { listedGet, type } from "@temperlang/core";
import { Box, Shape, Square, twice, unit } from "./shapes.js";

/**
 * @param {number} shapes
 * @returns {number}
 */
function bump(shapes) {
  return (shapes + 1) | 0;
}

class Circle extends type(Shape) {
  /** @type {number} */
  #r;

  /** @param {number} r */
  constructor(r) {
    super();
    this.#r = r;
  }

  /** @returns {number} */
  get r() {
    return this.#r;
  }

  /** @returns {number} */
  area() {
    return Math.imul(Math.imul(3, this.#r), this.#r);
  }
}

console.log(unit.toString());
console.log(twice(21).toString());
/** @type {Box<number>} */
const box = new Box(3);
/** @type {readonly Shape[]} */
const squares = Object.freeze([new Square(4), new Square(unit), Square.sized(5)]);
{
  const length = squares.length;
  let i = 0;
  while (i < length) {
    const s = listedGet(/** @type {Shape[]} */ (squares), i);
    i = (i + 1) | 0;
    console.log(`area ${s.area()}`);
  }
}
/** @type {readonly Shape[]} */
const mixed = Object.freeze([new Circle(2), new Square(2)]);
{
  const length2 = mixed.length;
  let i2 = 0;
  while (i2 < length2) {
    const s2 = listedGet(/** @type {Shape[]} */ (mixed), i2);
    i2 = (i2 + 1) | 0;
    {
      if (s2 instanceof Square) {
        console.log(`a square of ${/** @type {Square} */ (/** @type {unknown} */ (s2)).area()}`);
      } else {
        console.log(`something else of ${s2.area()}`);
      }
    }
  }
}
console.log(bump(box.value).toString());
