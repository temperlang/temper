import {
  isBubble,
  requireInstanceOf,
  requireNotNull,
  type,
} from "@temperlang/core";

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
  /** @type {string} */
  let t$0;
  try {
    t$0 = requireInstanceOf(x, Fuji).name;
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    t$0 = "Cast failed!";
  }
  console.log(t$0);
  const y = x;
  /** @type {string} */
  let t$1;
  try {
    t$1 = requireInstanceOf(y, Apple).name;
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    t$1 = "Cast from AnyValue failed!";
  }
  console.log(t$1);
  /** @type {string} */
  let t$2;
  try {
    t$2 = requireInstanceOf(y, Gala).name;
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    t$2 = "Cast from AnyValue failed!";
  }
  console.log(t$2);
  /** @type {string} */
  let t$3;
  try {
    t$3 = requireInstanceOf(y, Fuji).name;
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    t$3 = "Cast from AnyValue failed!";
  }
  console.log(t$3);
  x = null;
  /** @type {string} */
  let t$4;
  try {
    requireNotNull(x);
    t$4 = "Tasty nothing.";
  } catch (e$) {
    if (!isBubble(e$)) throw e$;
    t$4 = "Was null!";
  }
  console.log(t$4);
}
export const thing0 = false;
export const thing1 = 1;
export const thing2 = "two";
export const thing3 = 3.14;
