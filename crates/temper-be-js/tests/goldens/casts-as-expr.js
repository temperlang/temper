import { requireInstanceOf, requireNotNull, type } from "@temperlang/core";

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
export const thing0 = false;
export const thing1 = 1;
export const thing2 = "two";
export const thing3 = 3.14;
