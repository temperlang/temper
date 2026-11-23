# Lists

Sequences of values: the read-only view `Listed`, the immutable `List`, the
growable `ListBuilder`, and the double-ended queue `Deque`.

## Listed

*Listed* is an immutable view of a random-access sequence of values.
A `Listed<String>` may contain strings, a `Listed<Boolean>` may contain
booleans, etc.

The backing object might or might not be mutable. During a function call, an
argument of type `Listed` shouldn't be modified concurrently, although it
might be modified after the end of the call, so receivers *shouldn't* keep
any references to a `Listed` parameter for later use, unless accounting for
possible changes or according to specific contracts defined for particular
use cases.

If you want to keep a reference to a `Listed` argument, it's often best to
call `toList` on it and retain that value instead.

Usually, don't return type `Listed`. It's usually best to return type `List`.

    @connected
    export sealed interface Listed<T> {
      /**
       * The *length* of a listed is the number of items it contains.
       *
       * ```temper
       * let myList: List<String> = ["foo", "bar"];
       * myList.length == 2
       * // ✅ true
       * ```
       */
      @connected
      public get length(): Int;

      /** True if this contains no elements. */
      @connected
      public get isEmpty(): Boolean { length == 0 }

      /**
       * The i<sup>th</sup> element, or [snippet/builtin/Bubble] if *i* is outside the range \[0, [length]\).
       *
       * ```temper
       * let ls = ["a", "b", "c"]; // List of length 3
       *
       * // The list is zero-indexed.
       * console.log(ls[0]); //!outputs "a"
       * console.log(ls[1]); //!outputs "b"
       * // ✅
       * ```
       *
       * Out of bounds results in panic. Check in advance when handling indices of
       * uncertain origin. Panics can't be handled by code in Temper, although some
       * backend languages can handle their particular implementations of panic.
       *
       * ```temper inert
       * // console.log(ls[-1]);
       * // console.log(ls[ 3]);
       * ```
       */
      @connected
      public get(index: Int): T;

      @connected
      public getOr(index: Int, fallback: T): T;

      /**
       * A new list containing all and only elements from [begin] inclusive
       * to just before [end] exclusive.
       */
      @connected
      public slice(begin: Int, end: Int): List<T>;

      /**
       * Provides an immutable `List` with the values of this `Listed`. If this is
       * already a `List`, typically just returns `this` without any copying.
       */
      @connected
      public toList(): List<T>;

      /**
       * Always creates a new object and backing buffer even if this is already a
       * `ListBuilder`.
       */
      @connected
      public toListBuilder(): ListBuilder<T> {
        let result: ListBuilder<T> = new ListBuilder<T>();
        result.addAll(this);
        result
      }

      @connected
      public filter(predicate: fn (T): Boolean): List<T>;

      /**
       * Reduce to a single value, starting from the first item in this list.
       * Panic if empty.
       */
      @connected
      public reduce(accumulate: fn (T, T): T): T {
        listedReduceFromIndex(this, this[0], 1, accumulate)
      }

      @connected
      public join(separator: String, stringify: fn (T): String): String;

      /**
       * Returns a sorted version of this listed. Uses stable sorting.
       */
      @connected
      public sorted(compare: fn (T, T): Int): List<T>;
    }

    // Listed's generic methods are extensions, `xs.map { … }` calling
    // `listedMap(xs) { … }`: an interface's methods may be overridden, so
    // they can't be generic, in core as in any library.

## Listed extensions

`listedMap` (`listed.map { … }`) maps elements using [transform].

    @extension("map")
    @connected
    export let listedMap<T, O>(listed: Listed<T>, transform: fn (T): O): List<O> {
      let result = new ListBuilder<O>();
      for (var i = 0; i < listed.length; i += 1) {
        result.add(transform(listed[i]));
      }
      result.toList()
    }

`listedReduceFrom` (`listed.reduceFrom(…)`) reduces to a single value, starting from the given initial value.

    @extension("reduceFrom")
    @connected
    export let listedReduceFrom<T, O>(listed: Listed<T>, initial: O, accumulate: fn (O, T): O): O {
      listedReduceFromIndex(listed, initial, 0, accumulate)
    }

`listedReduceFromIndex` (`listed.reduceFromIndex(…)`) reduces to a single value, starting from the given initial value and
the item at [index].

    @extension("reduceFromIndex")
    @connected
    export let listedReduceFromIndex<T, O>(listed: Listed<T>, initial: O, index: Int, accumulate: fn (O, T): O): O {
      var result = initial;
      for (var i = index; i < listed.length; i += 1) {
        result = accumulate(result, listed[i]);
      }
      result
    }

## List

*List* is a random-access, immutable sequence of values. List
literals using `[...]` syntax represent immutable `List` instances. For
mutable random-access sequences, see `ListBuilder`.

See also `Listed` for recommendations on `List` vs `Listed` use.

    @connected
    @partialImu
    @mayDowncastTo(false) // Just use Listed::toList if immutable wanted.
    export class List<@out T> extends Listed<T> {
      private constructor();

      /** Same as `[...]` syntax but provides an explicit type argument. */
      public static nym`of` = _listify;

      @connected
      public get length(): Int;

      /** Panic on index out of bounds. */
      @connected
      public get(index: Int): T;

      @connected
      public toList(): List<T> { this }

      @connected
      public toListBuilder(): ListBuilder<T>;

      // TODO: Move this onto Listed once iterating while the body mutates the
      // list has settled semantics.
      @connected
      public forEach(@inlineUnrealizedGoal let body: fn (T): Void): Void {
        let n = length;
        var i = 0;
        while (i < n) {
          let el: T = get(i);
          i += 1;
          body(el);
        }
      }
    }

## ListBuilder

*ListBuilder* is a random-access, mutable, growable sequence of
values.

    @connected
    @mayDowncastTo(false) // Provides a bit of protection at least within Temper.
    export class ListBuilder<T> extends Listed<T> {

      @connected
      public constructor();
      // TODO: Maybe a constructor taking initial content, now that default
      // arguments work: `constructor(initialContent: Listed<T> = [])`.

      /** Panics for `at` less than 0 or greater than `length`. */
      @connected
      public add(newValue: T, at: Int = length): Void;

      /**
       * Panics for `at` less than 0 or greater than `length`. Given this
       * builder, adds a copy of its values.
       */
      @connected
      public addAll(newValues: Listed<T>, at: Int = length): Void;

      /** Removes all values, retaining internal capacity where possible. */
      @connected
      public clear(): Void;

      /**
       * Removes the last value if any, and returns it. Expected to be constant
       * time. Panics if empty.
       */
      @connected
      public removeLast(): T;

      /**
       * Reverses this list builder in place.
       */
      @connected
      public reverse(): Void;

      /** Panics on index out of bounds. */
      @connected
      public set(index: Int, newValue: T): Void;

      /**
       * Sorts in place. Uses stable sorting. Panics if `compare` adds or
       * removes values.
       */
      @connected
      public sort(compare: fn (T, T): Int): Void;

      /**
       * Removes `removeCount` items starting at `index`, or until the end of the
       * current items. Returns the removed items, and `newValues` are put in their
       * place, as they were before the call if they're this builder. The `index`
       * is clamped to current bounds.
       */
      @connected
      public splice(
        index: Int = 0,
        removeCount: Int = length,
        newValues: Listed<T> = [],
      ): List<T>;

      @connected
      public get length(): Int;

      @connected
      public toList(): List<T>;

      @connected
      public toListBuilder(): ListBuilder<T>;
    }

## Deque

*Deque* is a double-ended queue that allows for breadth-first processing via
efficient adds at the end and efficient removal from the front.

```temper inert
let deque = new Deque<String>();
deque.add("a");
deque.add("b");

console.log(deque.removeFirst()); //!outputs "a"

deque.add("c");
deque.add("d");

while (!deque.isEmpty) {
  console.log(deque.removeFirst()); //!outputs ["b", "c", "d"]
}
// ✅
```

    @connected
    export class Deque<T> {

      @connected
      public constructor();

      /**
       * Add an element at the end.
       */
      @connected
      public add(element: T): Void;

      /**
       * True if this contains no elements.
       */
      @connected
      public get isEmpty(): Boolean;

      /**
       * Removes and returns the first element. Panics if empty.
       */
      @connected
      public removeFirst(): T;
    }
