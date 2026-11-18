import { listedJoin, listedMap, type } from "@temperlang/core";
import { Circle, Rectangle, Stringable } from "./extra-class.js";

function bother() {
  const whatIAm = new Moreover(1);
  const moreover = new Moreover(2, 3);
  console.log(`Moreover: ${moreover.toString()}`);
  const moreover2 = new Moreover(4);
  console.log(`Moreover again: ${moreover2.toString()}`);
  const moreover3 = new Moreover(null, 7);
  console.log(`Moreover yet again: ${moreover3.toString()}`);
  const whatever = new Whatever(5);
  console.log(`Whatever: ${whatever.blah}`);
}

export class Person extends type(Stringable) {
  /** @type {string} */
  #name;
  /** @type {number} */
  #age;

  /**
   * @param {{ name: string, age?: number | null }} props
   * @returns {Person}
   */
  static new(props) {
    return new Person(props.name, props.age);
  }

  /**
   * @param {string} name
   * @param {number | null} [age]
   */
  constructor(name, age) {
    super();
    age ??= -100;
    this.#name = name;
    this.#age = age;
  }

  /** @returns {string} */
  get name() {
    return this.#name;
  }

  /** @returns {number} */
  get age() {
    return this.#age;
  }

  /** @returns {string} */
  toString() {
    const ageText = listedJoin(/** @type {number[]} */ (Object.freeze([0])), "", (unused) => this.#age.toString());
    return `Person ${this.#name} of age ${ageText}`;
  }

  /**
   * @param {Person} other
   * @returns {boolean}
   */
  eq(other) {
    if (this.#name === other.#name) {
      return this.#age === other.#age;
    } else {
      return false;
    }
  }
}

class Whatever extends type() {
  /** @type {string} */
  #blah;

  /** @param {number} blech */
  constructor(blech) {
    super();
    this.#blah = blech.toString();
  }

  /** @returns {string} */
  get blah() {
    return this.#blah;
  }
}

class Moreover extends type() {
  /** @type {number} */
  #blech;
  /** @type {number} */
  #bling;

  /**
   * @param {{ blech?: number | null, bling?: number | null }} props
   * @returns {Moreover}
   */
  static new(props) {
    return new Moreover(props.blech, props.bling);
  }

  /**
   * @param {number | null} [blech]
   * @param {number | null} [bling]
   */
  constructor(blech, bling) {
    super();
    blech ??= 0;
    bling ??= 0;
    this.#blech = blech;
    this.#bling = bling;
  }

  /** @returns {number} */
  get blech() {
    return this.#blech;
  }

  /** @returns {number} */
  get bling() {
    return this.#bling;
  }

  /** @returns {string} */
  toString() {
    return `${this.#blech} ${this.#bling}`;
  }
}

const person1 = new Person("Alice", 50);
const person2 = new Person("Bob", 45);
const circle1 = new Circle(2);
const circle2 = new Circle(1);
/** @type {readonly Stringable[]} */
const things = Object.freeze([person1, person2, circle1, circle2]);

/**
 * @param {Stringable} thing
 * @returns {number}
 */
function fn(thing) {
  console.log(thing.toString());
  return 0;
}

listedMap(/** @type {Stringable[]} */ (things), fn);
new Whatever(456);
new Whatever(321);
new Rectangle(7, 8);
bother();
const whatever = new Whatever(6);
console.log(`Whatever again: ${whatever.blah}`);
const personAgain = new Person("Carrie", 40);
const name = personAgain.name;
const someAge = personAgain.age;
console.log(`name is ${name} and age is ${someAge}`);
const personMore = new Person(name, 35);
console.log(personMore.toString());
const twin = new Person(person1.name, person1.age);
console.log(`Some are twins: ${person1.eq(twin)}`);
console.log(`All are twins: ${person2.eq(twin)}`);
