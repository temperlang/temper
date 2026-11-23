# Collections

Collections beyond core's lists and maps: the first-in, first-out
`Queue`, and the growable `BitVector`.

```temper inert
let { Queue, BitVector } = import("std/collections");
```

Each connects to a type of the backend's own where it has one (a
`java.util.Deque` and a `java.util.BitSet` in Java, for example). Their
Temper bodies say what they mean everywhere else. Like core's connected
types, neither may be the target of `is` or `as`.

## Queue

*Queue* is a first-in, first-out queue, for breadth-first processing:
adds go at the end, and removals come from the front, both efficiently.

```temper inert
let { Queue } = import("std/collections");

let queue = new Queue<String>();
queue.add("a");
queue.add("b");

console.log(queue.removeFirst()); //!outputs "a"

queue.add("c");
queue.add("d");

while (!queue.isEmpty) {
  console.log(queue.removeFirst()); //!outputs ["b", "c", "d"]
}
// ✅
```

    @connected
    export class Queue<T> {

Its items, from `head` on; those before `head` are removed already, and
dropped once they're at least half of the list.

      private let items: ListBuilder<T>;
      private var head: Int;

      @connected
      public constructor() {
        items = new ListBuilder<T>();
        head = 0;
      }

      /**
       * Add an element at the end.
       */
      @connected
      public add(element: T): Void {
        items.add(element);
      }

      /**
       * True if this contains no elements.
       */
      @connected
      public get isEmpty(): Boolean {
        head >= items.length
      }

      /**
       * Removes and returns the first element. Panics if empty.
       */
      @connected
      public removeFirst(): T {
        let first = items[head];
        head += 1;
        let length = items.length;
        if (head == length) {
          items.clear();
          head = 0;
        } else if (head >= 16 && head * 2 >= length) {
          items.splice(0, head);
          head = 0;
        }
        first
      }
    }

## BitVector

*BitVector* is a growable vector of bits, all `false` until set. It
suits bits mostly within \[0, capacity), where storing each bit costs
less than a set of the true ones would; it doesn't assume a bit is
likelier set when its neighbors are.

    @connected
    export class BitVector {
      private let bits: ListBuilder<Boolean>;

      /** A bit vector with room for [capacity] bits, all `false`. */
      @connected
      public constructor(capacity: Int) {
        bits = new ListBuilder<Boolean>();
        for (var i = 0; i < capacity; i += 1) {
          bits.add(false);
        }
      }

      /**
       * The bit at the given [index].  If a bit has not previously been set,
       * including at a negative index, it is `false`.
       */
      @connected
      public get(index: Int): Boolean {
        if (0 <= index && index < bits.length) {
          bits[index]
        } else {
          false
        }
      }

      /**
       * Sets the bit at [index] to [newValue], growing as needed.
       * After this, [get],  given [index], will return [newValue].
       * Panics if [index] is negative.
       *
       * ```temper
       * let { BitVector } = import("std/collections");
       * let bitVector = new BitVector(16);
       * console.log(bitVector.get(3).toString()); //!outputs "false"
       * bitVector.set(3, true);
       * console.log(bitVector.get(3).toString()); //!outputs "true"
       * bitVector[40] = true;
       * console.log(bitVector[40].toString()); //!outputs "true"
       * bitVector.set(3, false);
       * console.log(bitVector.get(3).toString()); //!outputs "false"
       * // ✅
       * ```
       */
      @connected
      public set(index: Int, newValue: Boolean): Void {
        let bits = this.bits;
        var limit = bits.length;
        if (limit <= index) {
          if (!newValue) {
            // Outside storage is already considered false.
            return;
          }
          while (limit <= index) {
            bits.add(false);
            limit += 1;
          }
        }
        // Panics for a negative index.
        bits.set(index, newValue);
      }
    }
