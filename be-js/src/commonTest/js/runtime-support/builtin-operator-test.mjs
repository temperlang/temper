import {
  divIntInt,
  cmpGeneric,
} from '../../../commonMain/resources/lang/temper/be/js/temper-core/index.js';
import {describe, it} from 'mocha';
import {expect} from 'chai';

describe("BuiltinOperatorId", () => {
  describe("divIntInt", () => {
    it("negative", () => {
      expect(0).to.equal(divIntInt(-1, 2));
      expect(-2).to.equal(divIntInt(-4, 2));
      expect(-2).to.equal(divIntInt(-5, 2));
    });
    it("positive", () => {
      expect(0).to.equal(divIntInt(1, 2));
      expect(2).to.equal(divIntInt(4, 2));
      expect(2).to.equal(divIntInt(5, 2));
    });
    it("zero", () => {
      expect(0).to.equal(divIntInt(0, 1));
    });
    it("bubble", () => {
      expect(() => divIntInt(1, 0))
        .to.throw();
    });
  });
  describe("generic comparisons", () => {
    it("cmpGeneric + friends", () => {
      let inOrder = [
        [-1, 0, 0.5, 1],
        ["a", "b", "\uD7FF", "\uE000", "\uFFFE", "\uFFFF", "\uD800\uDC00"],
        [false, true],
      ];
      for (let mutuallyComparable of inOrder) {
        for (let i = 1, n = mutuallyComparable.length; i < n; ++i) {
          let a = mutuallyComparable[i - 1];
          let b = mutuallyComparable[i];

          expect(cmpGeneric(a, b)).to.be.lt(0);
          expect(cmpGeneric(a, a)).to.equal(0);
          expect(cmpGeneric(b, a)).to.be.gt(0);
        }
      }
    });
  });
});
