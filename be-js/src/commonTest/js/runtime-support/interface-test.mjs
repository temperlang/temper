
import {type} from '../../../commonMain/resources/lang/temper/be/js/temper-core/index.js';

import {describe, it} from 'mocha';
import {expect} from 'chai';

describe("Interface Types", () => {
  it("Simple", () => {
    class Inter extends type() {}

    class Foo extends Inter {
      constructor() {
        super();
      }
    }

    expect(new Foo()).to.be.an.instanceOf(Inter);
  });

  it("Shadowed Method", () => {
    class A extends type() {
      value() {
        return 'A';
      }
    }

    class B extends type(A) {
      value() {
        return 'B'
      }
    }

    class C extends type(B) {
      constructor() {
        super();
      }
    }

    expect(new C().value()).to.equal("B");
  });

  it("Diamond Problem", () => {
    class A extends type() {
      value() {
        return 'A';
      }
    }

    class B extends type(A) {
      value() {
        return 'B';
      }
    }

    class AB extends type(A, B) {}
    class BA extends type(B, A) {}

    expect(new AB().value()).to.equal("A");
    expect(new BA().value()).to.equal("B");
  });

  it("type() extends non-type()", () => {
    class Base {}
    class Sub extends type(Base) {}
    expect(new Sub()).to.not.be.an.instanceOf(Base);
  });
});
