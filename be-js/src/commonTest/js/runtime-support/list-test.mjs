import {
  listBuilderAdd,
  listedJoin,
  listBuilderReverse,
  listedSlice,
} from '../../../commonMain/resources/lang/temper/be/js/temper-core/index.js';
import {describe, it} from 'mocha';
import {expect} from 'chai';

describe("List", () => {
  describe("join", () => {
    it("empty", () => {
      expect(
        listedJoin([], ", ", (_) => "?")
      ).to.deep.equal("");
    });
    it("one", () => {
      expect(
        listedJoin(
          [1, 20, 300, 4000],
          " ~~ ",
          (x) => `<${x.toString(16)}>`
        )
      ).to.deep.equal("<1> ~~ <14> ~~ <12c> ~~ <fa0>");
    });
    it("failureToStringify", () => {
      expect(
        () => listedJoin(
          [1, 2, 3],
          " . ",
          (x) => {
            if (x === 2) throw Error();
            return ".";
          }
        )
      ).to.throw();
    });
  });
  describe("reverse", () => {
    it("0", () => {
      let ls = [];
      listBuilderReverse(ls);
      expect(ls).to.deep.equal([])
    });
    it("1", () => {
      let ls = [0];
      listBuilderReverse(ls);
      expect(ls).to.deep.equal([0])
    });
    it("2", () => {
      let ls = [0, 1];
      listBuilderReverse(ls);
      expect(ls).to.deep.equal([1, 0])
    });
    it("3", () => {
      let ls = [0, 1, 2];
      listBuilderReverse(ls);
      expect(ls).to.deep.equal([2, 1, 0])
    });
    it("4", () => {
      let ls = [0, 1, 2, 3];
      listBuilderReverse(ls);
      expect(ls).to.deep.equal([3, 2, 1, 0])
    });
  });
  it("slice", () => {
    let ls = [0, 1, 2, 3];
    let got = [];
    let want = [];
    for (let i = -1; i < 6; ++i) {
      for (let j = -1; j < 6; ++j) {
        let between = [];
        for (let k = i; k < j; ++k) {
          if (0 <= k && k < ls.length) {
            between.push(k);
          }
        }
        got.push({ i, j, ls: listedSlice(ls, i, j) });
        want.push({ i, j, ls: between });
      }
    }
    expect(got).to.deep.equal(want);
  });
  it("add", () => {
    let ls = [];
    listBuilderAdd(ls, 0);
    listBuilderAdd(ls, 1);
    expect(ls).to.deep.equal([0, 1]);
  });
});
