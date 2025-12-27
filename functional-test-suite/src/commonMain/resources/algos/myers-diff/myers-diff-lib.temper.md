# Myers Diff

Each [change][Change] either adds its [items], removes them, or,
if they are present in both inputs, accepts them.

Also, once we support enums, we should use them here.

```temper inert
    export enum ChangeType {
      Deletion,
      Addition,
      Unchanged;
    }
```

    export let ChangeType = String;
    export let ChangeTypeDeletion:  ChangeType = "Deletion";
    export let ChangeTypeAddition:  ChangeType = "Addition";
    export let ChangeTypeUnchanged: ChangeType = "Unchanged";

Each change consists of a non-empty run of adjacent lines (or diff items, `<T>`s).

    export class Change<T /* -| Change<T> | List<T> */>(
      public type: ChangeType,
      /** Index into the left input list. */
      public leftIndex: Int,
      /** Index into the right input list. */
      public rightIndex: Int,
      public items: List<T>,
    ) {}

A patch is a list of [changes][Change].

    export class Patch<T /* -| Change<T> | Patch<T> */>(
      public changes: List<Change<T>>
    ) {}

## Finding differences

The diff function compares two inputs, and computes a [Patch]: the changes
from [left] required to derive [right].

    export let diff<T extends Equatable /*-| Change<T> | List<T> | Patch<T> */>(
      left: List<T>,
      right: List<T>,
      /** Are two items the same? */
      eq: fn (T, T): Boolean = fn (a: T, b: T): Boolean { a == b }
    ): Patch<T> throws Bubble {

<details><summary>implementation of Myers's diff algorithm</summary>

TODO How do we indicate attached doc comments with md text?

      /**
       * A linked-list form of a path through the edit graph
       * in reverse order.
       */
      class DiffPath(
        public leftIndex: Int,
        public rightIndex: Int,
        public changeType: String,
        public previous: DiffPath?,
      ) {}

Comments starting with "> " are quotes from
"An O(ND) Difference Algorithm and Its Variations",
www.xmailserver.org/diff2.pdf

      let leftLength = left.length;
      let rightLength = right.length;

We're doing a breadth-first search, so there's no need to visit a vertex twice.
The reached array keeps track of which we've reached.

      let reachedRowLength = leftLength + 1;
      let reachedColLength = rightLength + 1;
      let reached = new DenseBitVector(reachedRowLength * reachedColLength);

This has a bit for each vertex in the edit graph.

> The edit graph for A and B has a vertex at each point in the grid
> (x,y), x∈[0,N] and y∈[0,M]. The vertices of the edit graph are
> connected by horizontal, vertical, and diagonal directed edges
> to form a directed acyclic graph.

Keep track of paths that we need to examine and/or expand.
Diagonals are free but right and down transitions cost one.

      let costZeroEdges: Deque<DiffPath?> = new Deque<DiffPath?>();
      costZeroEdges.add(null);
      let costOneEdges: Deque<DiffPath> = new Deque<DiffPath>();

> The problem of finding a longest common subsequence (LCS) is
> equivalent to finding a path from (0,0) to (N,M) with the maximum
> number of diagonal edges

So we allocated an NxM array of reached edges, and we proceed
breadth-first, but process diagonal edges before any non-diagonals.
That lets us find the path with the fewest down and right transitions
in the edit graph that reaches (M, N).

      while (true) {
        let diffPath: DiffPath? =

Prefer zero cost paths to cost-one paths so that we always expand the
lowest cost path first.

          if (!costZeroEdges.isEmpty) {
            costZeroEdges.removeFirst()
          } else if (!costOneEdges.isEmpty) {
            costOneEdges.removeFirst()
          } else {
            null
          };

        let leftIndex: Int = diffPath?.leftIndex ?? 0;
        let rightIndex: Int = diffPath?.rightIndex ?? 0;
        if (leftIndex == leftLength && rightIndex == rightLength) {

We reached the end. Replay the path through the edit graph into an
edit script.

Unroll the linked-list that's in reverse order onto an array.

          let pathElements = new ListBuilder<DiffPath>();
          for (var pathElement: DiffPath? = diffPath; pathElement != null;) {
            let path = pathElement as DiffPath;
            pathElements.add(path);
            pathElement = path.previous
          }
          pathElements.reverse();

Group runs of steps by changeType and turn them into change elements.

          var leftPatchIndex: Int = 0;
          var rightPatchIndex: Int = 0;
          let changes = new ListBuilder<Change<T>>();
          let n: Int = pathElements.length;
          for (var i: Int = 0, end: Int; i < n; i = end) {
            let changeType: ChangeType = pathElements[i].changeType;

Find run with same changeType.

            end = i + 1;
            while (end < n && changeType == pathElements[end].changeType) {
              ++end;
            }
            let nItems = end - i;
            let items: List<T>;
            var nLeftItems: Int = 0;
            var nRightItems: Int = 0;
            if (changeType == ChangeTypeAddition) {
              items = right.slice(rightPatchIndex, rightPatchIndex + nItems);
              nRightItems = nItems;
            } else {
              items = left.slice(leftPatchIndex, leftPatchIndex + nItems);
              if (changeType == ChangeTypeUnchanged) {
                nRightItems = nItems;
              }
              nLeftItems = nItems;
            }
            changes.add(
              new Change<T>(
                changeType,
                leftPatchIndex,
                rightPatchIndex,
                items
              )
            );
            leftPatchIndex += nLeftItems;
            rightPatchIndex += nRightItems;
          }
          return new Patch<T>(changes.toList());
        }

Add adjacent diffPaths for the next possible addition, deletion,
or unchanged transition where possible.

        if (leftIndex < leftLength) {
          if (rightIndex < rightLength) {
            let sameReachedIndex: Int =
              leftIndex + 1 + (rightIndex + 1) * reachedRowLength;
            if (!reached[sameReachedIndex] && eq(left[leftIndex], right[rightIndex])) {
              reached[sameReachedIndex] = true;
              costZeroEdges.add(
                new DiffPath(
                  leftIndex + 1, rightIndex + 1,
                  ChangeTypeUnchanged, diffPath
                )
              );
            }
          }
          let delReachedIndex: Int = leftIndex + 1 + rightIndex * reachedRowLength;
          if (!reached[delReachedIndex]) {
            reached[delReachedIndex] = true;
            costOneEdges.add(new DiffPath(
              leftIndex + 1, rightIndex,
              ChangeTypeDeletion, diffPath
            ));
          }
        }
        if (rightIndex < rightLength) {
          let addReachedIndex: Int = leftIndex + (rightIndex + 1) * reachedRowLength;
          if (!reached[addReachedIndex]) {
            reached[addReachedIndex] = true;
            costOneEdges.add(new DiffPath(
              leftIndex, rightIndex + 1, ChangeTypeAddition, diffPath
            ));
          }
        }
      }
      // bubble(); // Actually unreachable.
    }

</details>

## Formatting

Once you've got a diff you can format it.

    export let formatPatch(patch: Patch<String>): String throws Bubble {

<details><summary>implementation of unified diff format</summary>

      var formatted: String = "";
      let changes = patch.changes;
      let n: Int = changes.length;
      for (var i: Int = 0; i < n; ++i) {
        let change: Change<String> = changes.get(i);
        let changeType: ChangeType = change.type;
        let items = change.items;
        let prefix: String = when (changeType) {
          ChangeTypeAddition -> "+";
          ChangeTypeDeletion -> "-";
          else -> " ";
        }
        let nItems = items.length;
        for (var j: Int = 0; j < nItems; ++j) {
          let item = items.get(j);
          formatted = "${formatted}${prefix}${item}\n";
        }
      }
      return formatted;
    }

</details>
