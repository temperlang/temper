import {
  dateConstructor,
  dateToday,
  dateYearsBetween,
} from '../../../commonMain/resources/lang/temper/be/js/temper-core/index.js';
import {describe, it} from 'mocha';
import {expect} from 'chai';

describe("Date", () => {
  describe("constructor", () => {
    it("2023-09-21", () => {
      let constructedFromJs = new Date(
        Date.UTC(2023, 9 - 1, 21, 0, 0, 0)
      );
      let constructedFromTemper =
          dateConstructor(2023, 9, 21);
      expect(constructedFromTemper).to.be.an.instanceof(Date);
      expect(constructedFromTemper.getTime())
        .to.equal(constructedFromJs.getTime());
    });
  });
  describe("today", () => {
    it("todayIsADate", () => {
      let d = dateToday();
      expect(d).to.be.an.instanceof(Date);
      expect(d.getUTCFullYear()).to.be.above(2022);
      expect(d.getUTCFullYear()).to.be.below(10000);
    });
  });
  describe("yearsBetween", () => {
    let cases = [
      [ 2020, 3,  1, 2021, 3,  1, 1 ],
      [ 2020, 3,  2, 2021, 3,  1, 0 ],
      [ 2020, 3,  1, 2021, 3,  2, 1 ],
      [ 2020, 2, 29, 2021, 2, 28, 0 ],
      [ 2020, 2, 28, 2021, 2, 28, 1 ],
    ];

    for (let [sy, sm, sd, ey, em, ed, want] of cases) {
      it(`between ${sy}-${sm}-${sd} and ${ey}-${em}-${ed}`, () => {
        let start = dateConstructor(sy, sm, sd);
        let end = dateConstructor(ey, em, ed);
        expect(dateYearsBetween(start, end)).to.equal(want);
      });
    }
  });
});
