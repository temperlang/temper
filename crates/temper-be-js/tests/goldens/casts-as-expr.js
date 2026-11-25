import { requireInstanceOf, requireNotNull, type } from "@temperlang/core";

/**
 * @param {unknown | null} v
 * @returns {string}
 */
function describe(v) {
  if (v != null && v instanceof Fuji) {
    return `Fuji ${/** @type {Fuji} */ (/** @type {unknown} */ (v)).name}`;
  }
  {
    if (v != null && v instanceof Apple) {
      return /** @type {Apple} */ (/** @type {unknown} */ (v)).name;
    } else {
      return "other";
    }
  }
}

class Apple extends type() {
  /** @type {string} */
  name = /** @type {any} */ (undefined);
}

class Fuji extends type(Apple) {
  /** @returns {string} */
  get name() {
    return "Fuji";
  }
}

class Gala extends type(Apple) {
  /** @returns {string} */
  get name() {
    return "Gala";
  }
}

{
  /** @type {Apple | null} */
  let x;
  x = new Fuji();
  console.log(requireNotNull(x).name);
  console.log(requireInstanceOf(x, Fuji).name);
  x = new Gala();
  console.log(x != null && x instanceof Fuji ? requireInstanceOf(x, Fuji).name : "Cast failed!");
  const y = x;
  console.log(requireInstanceOf(y, Apple).name);
  console.log(requireInstanceOf(y, Gala).name);
  x = null;
  console.log(x != null ? "Tasty nothing." : "Was null!");
}
export const thing2 = "two";
export const thing1 = 1;
{
  console.log(describe(new Fuji()));
  console.log(describe(new Gala()));
  console.log(describe(thing2));
  console.log(describe(thing1));
  console.log(describe(null));
}
export const thing0 = false;
export const thing3 = 3.14;
