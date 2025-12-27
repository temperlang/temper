import {
  diff,
  formatPatch,
} from './diff.mjs';
import {describe, it} from 'mocha';
import {expect} from 'chai';

describe('diff-lib', () => {
  describe('diff', () => {
    describe('empty lists', () => {
      it('left empty', () => {
        expect(
            diff([], [1, 2, 3]),
        ).to.deep.equal(
            {
              changes: [
                {
                  changeType: 'Addition',
                  leftIndex: 0,
                  rightIndex: 0,
                  items: [1, 2, 3],
                },
              ],
            },
        );
      });
      it('right empty', () => {
        expect(
            diff([1, 2, 3], []),
        ).to.deep.equal(
            {
              changes: [
                {
                  changeType: 'Deletion',
                  leftIndex: 0,
                  rightIndex: 0,
                  items: [1, 2, 3],
                },
              ],
            },
        );
      });
      it('both empty', () => {
        expect(
            diff([], []),
        ).to.deep.equal(
            {
              changes: [],
            },
        );
      });
    });
    it('Hello World', () => {
      expect(
          diff(
              'Hello,\nWorld!'.split('\n'),
              'Hello,\nCosmos!'.split('\n'),
          ),
      ).to.deep.equal(
          {
            changes: [
              {
                changeType: 'Unchanged',
                leftIndex: 0,
                rightIndex: 0,
                items: ['Hello,'],
              },
              {
                changeType: 'Deletion',
                leftIndex: 1,
                rightIndex: 1,
                items: ['World!'],
              },
              {
                changeType: 'Addition',
                leftIndex: 2,
                rightIndex: 1,
                items: ['Cosmos!'],
              },
            ],
          },
      );
    });
    it('Myers\'s example', () => {
      expect(
          diff(
              ['a', 'b', 'c', 'a', 'b', 'b', 'a'],
              ['c', 'b', 'a', 'b', 'a', 'c'],
          ),
      ).to.deep.equal(
          {
            changes: [
              {
                changeType: 'Deletion',
                leftIndex: 0,
                rightIndex: 0,
                items: ['a', 'b'],
              },
              {
                changeType: 'Unchanged',
                leftIndex: 2,
                rightIndex: 0,
                items: ['c'],
              },
              {
                changeType: 'Addition',
                leftIndex: 3,
                rightIndex: 1,
                items: ['b'],
              },
              {
                changeType: 'Unchanged',
                leftIndex: 3,
                rightIndex: 2,
                items: ['a', 'b'],
              },
              {
                changeType: 'Deletion',
                leftIndex: 5,
                rightIndex: 4,
                items: ['b'],
              },
              {
                changeType: 'Unchanged',
                leftIndex: 6,
                rightIndex: 4,
                items: ['a'],
              },
              {
                changeType: 'Addition',
                leftIndex: 7,
                rightIndex: 5,
                items: ['c'],
              },
            ],
          },
      );
    });
    it('Myers\'s example backwards', () => {
      expect(
          diff(
              ['c', 'b', 'a', 'b', 'a', 'c'],
              ['a', 'b', 'c', 'a', 'b', 'b', 'a'],
          ),
      ).to.deep.equal(
          // /usr/bin/diff produces something just like this per
          // $ diff -u <(echo 'c,b,a,b,a,c'   | tr , '\n') \
          //           <(echo 'a,b,c,a,b,b,a' | tr , '\n')
          {
            changes: [
              {
                changeType: 'Deletion',
                leftIndex: 0,
                rightIndex: 0,
                items: ['c'],
              },
              {
                changeType: 'Addition',
                leftIndex: 1,
                rightIndex: 0,
                items: ['a'],
              },
              {
                changeType: 'Unchanged',
                leftIndex: 1,
                rightIndex: 1,
                items: ['b'],
              },
              {
                changeType: 'Addition',
                leftIndex: 2,
                rightIndex: 2,
                items: ['c'],
              },
              {
                changeType: 'Unchanged',
                leftIndex: 2,
                rightIndex: 3,
                items: ['a', 'b'],
              },
              {
                changeType: 'Addition',
                leftIndex: 4,
                rightIndex: 5,
                items: ['b'],
              },
              {
                changeType: 'Unchanged',
                leftIndex: 4,
                rightIndex: 6,
                items: ['a'],
              },
              {
                changeType: 'Deletion',
                leftIndex: 5,
                rightIndex: 7,
                items: ['c'],
              },
            ],
          },
      );
    });
  });
  describe('format patch', () => {
    it('Myers\'s example', () => {
      expect(
          formatPatch(
              diff(
                  ['a', 'b', 'c', 'a', 'b', 'b', 'a'],
                  ['c', 'b', 'a', 'b', 'a', 'c'],
              ),
          ),
      ).to.equal(
          trim`
                |-a
                |-b
                | c
                |+b
                | a
                | b
                |-b
                | a
                |+c
                |
                `,
      );
    });
    it('Hello World', () => {
      expect(
          formatPatch(
              diff(
                  'Hello,\nWorld!'.split('\n'),
                  'Hello,\nCosmos!'.split('\n'),
              ),
          ),
      ).to.equal(
          trim`
                | Hello,
                |-World!
                |+Cosmos!
                |
                `,
      );
    });
  });
});

function trim([str]) {
  let trimmed = str.replace(/^(?:\r?\n|\r)|(?:\r?\n|\r)[ \t]*$/g, '');
  if (trimmed) {
    const [, spaces] = /^([ \t]*)[|]/.exec(trimmed);
    const matcher = new RegExp(String.raw`(^|\r?\n|\r)${spaces}[|]`, 'g');
    trimmed = trimmed.replace(matcher, '$1');
  }
  return trimmed;
}
