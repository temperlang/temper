
/**
 * A temper Super-Type.
 *
 * Returns a `Union`.
 * Temper types always extend a Union made by this function.
 * Only takes temper types as inputs.
 *
 * Examples:
 *
 * Basic usage with multiple inheritance.
 * ```js
 * class Plant extends type() {}
 * class Potato extends type(Plant) {}
 * class Snack extends type() {}
 * class Fry extends type(Potato, Snack) {}
 * ```
 *
 * The following will not work because Basic does not extend `type()`.
 * ```
 * class Basic {}
 * class Sub extends type(Basic) {}
 * ```
 *
 * The following will work.
 * ```
 * class Pet extends type() {}
 * class Dog extends Pet {}
 * ```
 *
 * type()
 * @param {typeof Union} superTypes
 * @return {Union}
 */
export const type = (...superTypes) => {
  const key = Symbol();

  class Union {
    static [Symbol.hasInstance] = (instance) => {
      return typeof instance === 'object' && instance !== null && key in instance;
    };
  }

  Union.prototype[key] = null;

  for (const superType of superTypes.reverse()) {
    let proto = Object.getPrototypeOf(superType.prototype);
    for (const sym of Object.getOwnPropertySymbols(proto)) {
      Union.prototype[sym] = null;
    }
    Object.defineProperties(Union.prototype, Object.getOwnPropertyDescriptors(proto));
    Object.defineProperties(Union.prototype, Object.getOwnPropertyDescriptors(superType.prototype));
  }

  return Union;
};
