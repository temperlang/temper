export class Patch/* <T -| Change<T> | Patch<T> >*/ {
  changes/* : List<Change<T>>*/;

  constructor(changes) {
    this.changes = changes;
  }

  toJSON() {
    return {
      'class': 'Patch',
      'changes': this.changes,
    };
  }

  toString() {
    return JSON.stringify(this);
  }
}

export class Change/* <T -| Change<T> | List<T> >*/ {
  changeType/* : ChangeType*/;
  /** Index into the left input list. */
  leftIndex/* : Int*/;
  /** Index into the right input list. */
  rightIndex/* : Int*/;
  items/* : List<T>*/;

  constructor(changeType, leftIndex, rightIndex, items) {
    this.changeType = changeType;
    this.leftIndex = leftIndex;
    this.rightIndex = rightIndex;
    this.items = items;
  }

  toString() {
    return JSON.stringify(this);
  }

  toJSON() {
    return {
      'class': 'Change',
      'changeType': this.changeType,
      'leftIndex': this.leftIndex,
      'rightIndex': this.rightIndex,
      'items': this.items,
    };
  }
}

/*
export enum class ChangeType {
    Addition;
    Deletion;
    Unchanged;
}
*/
export const ChangeType = {
  Addition: 'Addition',
  Deletion: 'Deletion',
  Unchanged: 'Unchanged',
};

const DEBUG = false;

export function diff/* <T -| Change<T> | List<T> | Patch<T> >*/(
    left/* : List<T>*/,
    right/* : List<T>*/,
    /**
     * Are two items the same?
     * @param {*} a compared to b
     * @param {*} b compared to a
     * @return {boolean}
     */
    eq/* : fn (T, T): Int = nym`==`*/ = (a, b) => a === b,
)/* : Patch<T>*/ {
  // Comments starting with "// > " are quotes from
  // "An O(ND) Difference Algorithm and Its Variations",
  // www.xmailserver.org/diff2.pdf

  const leftLength = left.length;
  const rightLength = right.length;

  // We're doing a breadth-first search, so there's no need to
  // visit a vertex twice.
  // The reached array keeps track of which we've reached.
  const reachedRowLength = leftLength || 1;
  const reachedColLength = rightLength;
  const reached = new Array(reachedRowLength * reachedColLength);
  reached.fill(false);
  // This has a bit for each vertex in the edit graph.
  // > The edit graph for A and B has a vertex at each point in the grid
  // > (x,y), x∈[0,N] and y∈[0,M]. The vertices of the edit graph are
  // > connected by horizontal, vertical, and diagonal directed edges
  // > to form a directed acyclic graph.

  // Keep track of paths that we need to examine and/or expand.
  // Diagonals are free but right and down transitions cost one.
  const costZeroEdges = [null];
  const costOneEdges = [];
  // > The problem of finding a longest common subsequence (LCS) is
  // > equivalent to finding a path from (0,0) to (N,M) with the maximum
  // > number of diagonal edges
  // So we allocated an NxM array of reached edges, and we proceed
  // breadth-first, but process diagnonal edges before any non-diagonals.
  // That lets us find the path with the fewest down and right transitions
  // in the edit graph that reaches (M, N).

  while (true) {
    const diffPath = (
            // Prefer zero cost paths to cost one paths so that we always expand
            // the lowest cost path first.
            costZeroEdges.length ? costZeroEdges : costOneEdges
    ).shift();
    const leftIndex = diffPath?.leftIndex || 0;
    const rightIndex = diffPath?.rightIndex || 0;
    if (DEBUG) {
      console.group(`DiffPath`);
      console.log(JSON.stringify(diffPath, null, 2));
      console.log(
          `reached=${reached} leftIndex=${leftIndex} rightIndex=${rightIndex}`,
      );
      console.groupEnd();
    }
    if (leftIndex === leftLength && rightIndex === rightLength) {
      // We reached the end.
      return diffPathToPatch(diffPath, left, right);
    }

    const costZeroLength = costZeroEdges.length;
    const costOneLength = costOneEdges.length;

    // Add adjacent diffPaths for the next possible addition, deletion,
    // or unchanged transition where possible.
    if (leftIndex < leftLength) {
      if (rightIndex < rightLength) {
        const sameReachedIndex =
                    leftIndex + 1 + (rightIndex + 1) * reachedRowLength;
        if (!reached[sameReachedIndex] &&
            eq(left[leftIndex], right[rightIndex])) {
          reached[sameReachedIndex] = true;
          costZeroEdges.push(new DiffPath(
              leftIndex + 1, rightIndex + 1,
              ChangeType.Unchanged, diffPath,
          ));
        }
      }
      const delReachedIndex = leftIndex + 1 + rightIndex * reachedRowLength;
      if (!reached[delReachedIndex]) {
        reached[delReachedIndex] = true;
        costOneEdges.push(new DiffPath(
            leftIndex + 1, rightIndex,
            ChangeType.Deletion, diffPath,
        ));
      }
    }
    if (rightIndex < rightLength) {
      const addReachedIndex = leftIndex + (rightIndex + 1) * reachedRowLength;
      if (!reached[addReachedIndex]) {
        reached[addReachedIndex] = true;
        costOneEdges.push(new DiffPath(
            leftIndex, rightIndex + 1, ChangeType.Addition, diffPath,
        ));
      }
    }

    if (DEBUG) {
      console.group(`after processing ${diffPath}`);
      console.group(`new cost zero`);
      for (let i = costZeroLength; i < costZeroEdges.length; ++i) {
        console.log(costZeroEdges[i]);
      }
      console.groupEnd();
      console.group(`new cost one`);
      for (let i = costOneLength; i < costOneEdges.length; ++i) {
        console.log(costOneEdges[i]);
      }
      console.groupEnd();
      console.groupEnd();
    }
  }
}

/**
 * A linked-list form of a path through the edit graph
 * in reverse order.
 */
class DiffPath {
  leftIndex/* : Int*/;
  rightIndex/* : Int*/;
  changeType/* : ChangeType*/;
  previous/* : DiffPath*/;

  constructor(leftIndex, rightIndex, changeType, previous) {
    this.leftIndex = leftIndex;
    this.rightIndex = rightIndex;
    this.changeType = changeType;
    this.previous = previous;
  }

  toString() {
    return JSON.stringify(this);
  }

  toJSON() {
    return {
      'class': 'DiffPath',
      'leftIndex': this.leftIndex,
      'rightIndex': this.rightIndex,
      'changeType': this.changeType,
      'previous': this.previous,
    };
  }
}

/**
 * Replay the path through the edit graph into an edit script.
 *
 * @param {DiffPath?} diffPath the path through the edit graph for left, right.
 * @param {List<T>} left the elements from the left side of the diff.
 * @param {List<T>} right
 * @return {Patch<T>}
 */
function diffPathToPatch(diffPath, left, right) {
  // Unroll the linked-list that's in reverse order onto an array.
  const pathElements = [];
  for (let pathElement = diffPath; pathElement !== null;
    pathElement = pathElement.previous) {
    pathElements.push(pathElement);
  }
  pathElements.reverse();
  if (DEBUG) {
    console.group('pathElements in order');
    pathElements.forEach((pathElement) => {
      console.log(pathElement);
    });
    console.groupEnd();
  }

  // Group runs of steps by changeType and turn them into
  // change elements.
  let leftPatchIndex = 0; let rightPatchIndex = 0;
  const changes = [];
  for (let i = 0, n = pathElements.length, end; i < n; i = end) {
    const {changeType} = pathElements[i];
    // Find run with same changeType.
    end = i + 1;
    while (end < n && changeType === pathElements[end].changeType) {
      ++end;
    }
    const nItems = end - i;
    let items;
    let nLeftItems = 0;
    let nRightItems = 0;
    if (changeType === ChangeType.Addition) {
      items = right.slice(rightPatchIndex, rightPatchIndex + nItems);
      nRightItems = nItems;
    } else {
      items = left.slice(leftPatchIndex, leftPatchIndex + nItems);
      nLeftItems = nItems;
      if (changeType === ChangeType.Unchanged) {
        nRightItems = nItems;
      }
    }
    if (DEBUG) {
      console.group(
          `run i=${i} end=${end}, changeType=${changeType
          }, left=${leftPatchIndex}, right=${rightPatchIndex}`,
      );
      items.forEach((item) => {
        console.log(JSON.stringify(item));
      });
      console.groupEnd();
    }
    changes.push(
        new Change(
            changeType,
            leftPatchIndex,
            rightPatchIndex,
            items,
        ),
    );
    leftPatchIndex += nLeftItems;
    rightPatchIndex += nRightItems;
  }
  return new Patch(changes);
}

export function formatPatch(
    patch, /* : Patch<String>*/
)/* : String*/ {
  let formatted = '';
  const {changes} = patch;
  for (let i = 0, n = changes.length; i < n; ++i) {
    const {changeType, items} = changes[i];
    let prefix;
    if (changeType === ChangeType.Addition) {
      prefix = '+';
    } else if (changeType == ChangeType.Deletion) {
      prefix = '-';
    } else {
      prefix = ' ';
    }
    items.forEach((item) => {
      formatted += prefix + `${item}`.replace(/\r\n?|\r/g, '$&:') + '\n';
    });
  }
  return formatted;
}
