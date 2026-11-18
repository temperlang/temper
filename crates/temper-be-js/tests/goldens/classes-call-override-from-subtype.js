import { type } from "@temperlang/core";

class Slappy extends type() {
  /** @type {number} */
  thingums = /** @type {any} */ (undefined);
}

class Slippy extends type(Slappy) {
  /** @returns {boolean} */
  checkOverridden() {
    return this.thingums === 1;
  }

  /** @returns {boolean} */
  checkOwn() {
    return this.thingumses === 2;
  }

  /** @returns {number} */
  get thingums() {
    return 3;
  }

  /** @returns {number} */
  get thingumses() {
    return 4;
  }
}

class A extends type() {
  /** @returns {string} */
  a() {
    return "A wins!";
  }

  /** @returns {string} */
  get thing() {
    return "A has thing!";
  }
}

class B extends type(A) {
  /** @type {() => string} */
  a = /** @type {any} */ (undefined);
  /** @type {string} */
  thing = /** @type {any} */ (undefined);
}

class C extends type(A) {
  /** @returns {string} */
  a() {
    return "C wins!";
  }

  /** @returns {string} */
  get thing() {
    return "C has thing!";
  }
}

class D extends type(B, C) {
  /** @returns {string} */
  a() {
    return C.prototype.a.call(this);
  }

  /** @returns {string} */
  get thing() {
    return Reflect.get(C.prototype, "thing", this);
  }
}

class ASplit extends type() {
  /** @returns {string} */
  a() {
    return "A wins!";
  }
}

class BSplit extends type(ASplit) {
  /** @type {() => string} */
  a = /** @type {any} */ (undefined);
}

class CSplit extends type() {
  /** @returns {string} */
  a() {
    return "C wins!";
  }
}

class DSplit extends type(BSplit, CSplit) {
  /** @returns {string} */
  a() {
    return CSplit.prototype.a.call(this);
  }
}

class ADeep extends type() {
  /** @returns {string} */
  a() {
    return "A wins!";
  }
}

class B1Deep extends type(ADeep) {
  /** @returns {string} */
  a() {
    return "B1 wins!";
  }
}

class B2Deep extends type(B1Deep) {
  /** @type {() => string} */
  a = /** @type {any} */ (undefined);
}

class CDeep extends type(ADeep) {
  /** @type {() => string} */
  a = /** @type {any} */ (undefined);
}

class DDeep extends type(B2Deep, CDeep) {
  /** @returns {string} */
  a() {
    return B1Deep.prototype.a.call(this);
  }
}

class EDeep extends type(CDeep, B2Deep) {
  /** @returns {string} */
  a() {
    return ADeep.prototype.a.call(this);
  }
}

const slippy = new Slippy();
const slappy = slippy;
console.log(slippy.thingums.toString());
console.log(slappy.thingums.toString());
console.log(slippy.thingumses.toString());
console.log(slippy.checkOverridden().toString());
console.log(slippy.checkOwn().toString());
console.log(new D().a());
console.log(new D().thing);
console.log(new DSplit().a());
console.log(new DDeep().a());
console.log(new EDeep().a());
