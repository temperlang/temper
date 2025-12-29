import {
  stringCountBetween,
  stringForEach,
  stringHasAtLeast,
  stringNext,
  stringPrev,
} from '../../../commonMain/resources/lang/temper/be/js/temper-core/index.js';
import {describe, it} from 'mocha';
import {expect} from 'chai';

const str = "κόσμε\ud834\ude0e--";
//            κ      ό       σ      μ      ε      \ud834\ude0e  -     -
let strCps = [0x3BA, 0x1F79, 0x3C3, 0x3BC, 0x3B5, 0x1D20E,      0x2D, 0x2D];

let validStringIndices = [0, 1, 2, 3, 4, 5, 7, 8, 9];
let validAndInvalidStringIndices = [-1, ...validStringIndices, 10];

describe('string helpers', () => {
  it('stringCountBetween', () => {
    for (let left of validAndInvalidStringIndices) {
      for (let right of validAndInvalidStringIndices) {
        let validLeft = Math.max(0, Math.min(str.length, left));
        let validRight = Math.max(left, Math.min(str.length, right));

        let actualCount = 0;
        for (let cp of str.substring(validLeft, validRight)) {
          actualCount += 1;
        }
        expect(
          stringCountBetween(str, left, right),
          `left=${left}, right=${right}`
        ).to.equal(actualCount);
      }
    }
  });
  it('hasAtLeast', () => {
    for (let left of validAndInvalidStringIndices) {
      for (let right of validAndInvalidStringIndices) {
        let validLeft = Math.max(0, Math.min(str.length, left));
        let validRight = Math.max(left, Math.min(str.length, right));

        let actualCount = 0;
        for (let cp of str.substring(validLeft, validRight)) {
          actualCount += 1;
        }

        let actualResults = {};
        let desiredResults = {};
        for (let minCount = -1; minCount < 12; ++minCount) {
          desiredResults[minCount] = minCount <= actualCount;
          actualResults[minCount] = stringHasAtLeast(str, left, right, minCount);
        }
        expect(
          actualResults,
          `left=${left}, right=${right}`
        ).to.deep.equal(desiredResults);
      }
    }
  });
  it('forEach', () => {
    let desired = [];
    for (let cp of str) {
      desired.push(cp.codePointAt(0));
    }
    let actual = [];
    stringForEach(str, (cp) => {
      actual.push(cp);
    });
    expect(actual).to.deep.equal(desired);
  });
  it('prev / next', () => {
    // From each valid index, compute the previous as long as we're monotonic,
    // and then compare.
    let nexts = [];
    {
      let i = 0;
      while (true) {
        nexts.push(i);
        let iNext = stringNext(str, i);
        if (i === iNext) { break }
        if (!(iNext > i)) {
          throw new Error(`stringNext: ${iNext} not monotonic wrt ${i}`);
        }
        i = iNext;
      }
    }

    let prevs = [];
    {
      let i = str.length;
      while (true) {
        prevs.push(i);
        let iPrev = stringPrev(str, i);
        if (i === iPrev) { break }
        if (!(iPrev < i)) {
          throw new Error(`stringPrev: ${iPrev} not monotonic wrt ${i}`);
        }
        i = iPrev;
      }
    }

    let validStringIndicesRev = [...validStringIndices];
    validStringIndicesRev.reverse();

    expect(
      {
        nexts: validStringIndices,
        prevs: validStringIndicesRev,
      }
    ).to.deep.equal(
      {
        nexts: nexts,
        prevs: prevs,
      }
    );
  });
});
