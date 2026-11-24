import { empty, type } from "@temperlang/core";

/** @returns {string} */
function changeRepeat() {
  repeat = true;
  return message;
}

/** @returns {boolean} */
function changeMessage() {
  message = "Namaste!";
  return repeat;
}

/**
 * @template T
 * @param {number} n
 * @param {(arg0: number) => T} transform
 * @param {CalcOptions | null} [options]
 * @returns {T}
 */
function calc(n, transform, options) {
  options ??= new CalcOptions();
  const origin = options.origin;
  const scale = options.scale;
  return transform(Math.imul((n - origin) | 0, scale));
}

export class Greet extends type() {
  /**
   * @param {{ message?: string | null, repeat?: boolean | null }} props
   * @returns {Greet}
   */
  static new(props) {
    return new Greet(props.message, props.repeat);
  }

  /**
   * @param {string | null} [message]
   * @param {boolean | null} [repeat]
   */
  constructor(message, repeat) {
    super();
    message ??= "Hi!";
    repeat ??= false;
    console.log(message);
    if (repeat) {
      console.log(message);
    }
  }
}

class Person extends type() {
  /** @type {string} */
  #name;
  /** @type {number} */
  #age;

  /**
   * @param {{ name?: string | null, age?: number | null }} props
   * @returns {Person}
   */
  static new(props) {
    return new Person(props.name, props.age);
  }

  /**
   * @param {string | null} [name]
   * @param {number | null} [age]
   */
  constructor(name, age) {
    super();
    name ??= "Alice";
    age ??= 40;
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
    return `{ class: Person, name: ${this.#name}, age: ${this.#age} }`;
  }

  /** @param {string | null} [greeting] */
  greet(greeting) {
    greeting ??= "Hi";
    console.log(`${greeting}, ${this.#name}`);
  }
}

class CalcOptions extends type() {
  /** @type {number} */
  #scale;
  /** @type {number} */
  #origin;

  /**
   * @param {{ scale?: number | null, origin?: number | null }} props
   * @returns {CalcOptions}
   */
  static new(props) {
    return new CalcOptions(props.scale, props.origin);
  }

  /**
   * @param {number | null} [scale]
   * @param {number | null} [origin]
   */
  constructor(scale, origin) {
    super();
    scale ??= 1;
    origin ??= 0;
    this.#scale = scale;
    this.#origin = origin;
  }

  /** @returns {number} */
  get scale() {
    return this.#scale;
  }

  /** @returns {number} */
  get origin() {
    return this.#origin;
  }
}

new Greet();
new Greet("Bye!");
new Greet("Hola!", true);
new Greet("Salut!");
new Greet(null, true);
new Greet(null, true);
{
  const arg = true;
  new Greet("Ni hao!", arg);
}
let message = "Hi!";
let repeat = false;
{
  const arg2 = changeMessage();
  const arg3 = changeRepeat();
  new Greet(arg3, arg2);
}
console.log(new Person().toString());
console.log(new Person("Bob").toString());
console.log(new Person("Carrie", 50).toString());
new Person("Dan").greet("Hallo");
console.log(calc(2, (n) => n.toString()));
console.log(calc(2, (n) => n.toString(), new CalcOptions(3, 4)));
console.log(calc(2, (n) => n.toString(), new CalcOptions(3)));
console.log(calc(2, (n) => n.toString(), new CalcOptions(null, 4)));
console.log(calc(2, (n) => `<${n}>`, new CalcOptions(5)));

/**
 * @param {number} n
 * @returns {{}}
 */
function fn6(n) {
  console.log(n.toString());
  return empty();
}

calc(2, fn6, new CalcOptions(-2));
