import {
  dequeConstructor,
  dequeAdd,
  dequeIsEmpty,
  dequeRemoveFirst,
} from '../../../commonMain/resources/lang/temper/be/js/temper-core/index.js';
import {describe, it} from 'mocha';
import {expect} from 'chai';

describe("Deque", () => {
  describe("addAndRemove", () => {
    it("tenTen", () => {
      let deque = dequeConstructor();
      for (let i = 0; i < 10; ++i) {
        dequeAdd(deque, `${i}`);
      }
      for (let i = 0; i < 10; ++i) {
        expect(dequeIsEmpty(deque)).to.be.false;
        expect(dequeRemoveFirst(deque)).to.equal(`${i}`);
      }
      expect(dequeIsEmpty(deque)).to.be.true;
    });
    it("fuzzily", () => {
      // Throw a series of random adds and removes at two implementations:
      // - the one under test
      // - a slow but simple one
      // then see if they generate the same results.
      let slowButSimple = []; // Only slow on V8 AFAICT
      let deque = dequeConstructor();

      let itemCounter = 0;
      let wanted = [];
      let got = [];
      for (let i = 0; i < 10000; ++i) {
        let rand = Math.random();
        // 40% removes, 50% adds, 10% isEmpty tests
        if (rand < 0.1) {
          wanted.push(`empty:${slowButSimple.length === 0}`);
          got.push(`empty:${dequeIsEmpty(deque)}`);
        } else if (rand < 0.5) { // remove
          let shifted = slowButSimple.shift()
          wanted.push(shifted === undefined ? "FAIL" : shifted);
          let result;
          try {
            result = dequeRemoveFirst(deque);
          } catch (e) {
            result = "FAIL";
          }
          got.push(result);
        } else { // add
          let newItem = `${itemCounter++}`;
          slowButSimple.push(newItem);
          dequeAdd(deque, newItem);
          wanted.push(`Added ${newItem}`);
          got.push(`Added ${newItem}`);
        }
      }

      expect(got).to.deep.equals(wanted);
    });
  });
  it("emptyIsEmpty", () => {
    let deque = dequeConstructor();
    expect(dequeIsEmpty(deque)).to.be.true;
  });
});
