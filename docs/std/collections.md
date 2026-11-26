# `std/collections`: queues and bit vectors

```temper
let { Queue, BitVector } = import("std/collections");
```

Collections beyond core's lists and maps. Each backend uses its
language's own type where it has one, so these pass to and from host
code as that type:

| Backend | `Queue<T>` | `BitVector` |
|---|---|---|
| C++ | `std::shared_ptr<std::deque<T>>` | `std::shared_ptr<std::vector<bool>>` |
| C# | `Queue<T>` | `BitArray` |
| Java | `java.util.Deque<T>` | `java.util.BitSet` |
| JavaScript | an array | the runtime's `BitVector` |
| Lua | the runtime's `temper.Queue` | the runtime's `temper.BitVector` |
| Python | `collections.deque` | `temper_core.BitVector` |
| Rust | `temper_core::Queue<T>` | `temper_core::BitVector` |

Since several Temper types may share a host type, neither can be the
target of `is` or `as`.

## `Queue`

A first-in, first-out queue, for breadth-first processing: `add` puts
an item at the end, and `removeFirst` takes one from the front.

```temper
let { Queue } = import("std/collections");

let queue = new Queue<String>();
queue.add("a");
queue.add("b");
console.log(queue.removeFirst()); //!outputs "a"
console.log(queue.isEmpty.toString()); //!outputs "false"
// ✅
```

| Member | What it does |
|---|---|
| `new Queue<T>()` | An empty queue |
| `add(item)` | Adds `item` at the end |
| `isEmpty` | Whether it has no items |
| `removeFirst()` | Removes and returns the first item; panics if empty |

## `BitVector`

A growable vector of bits, all `false` until set.

```temper
let { BitVector } = import("std/collections");

let bits = new BitVector(16);
bits[3] = true;
bits.set(100, true); // Grows to fit.
console.log("${bits[3]} ${bits[4]} ${bits[100]} ${bits[-1]}"); //!outputs "true false true false"
// ✅
```

| Member | What it does |
|---|---|
| `new BitVector(capacity)` | Room for `capacity` bits, all `false`; a negative capacity is none |
| `get(index)`, `bits[index]` | The bit; `false` if never set, past the end, or at a negative index |
| `set(index, value)`, `bits[index] = value` | Sets the bit, growing as needed; panics for a negative index |

## From core's `Deque` and `DenseBitVector`

These were core's `Deque` and `DenseBitVector`, which code used without
an import. Code that names them now gets an error naming the import to
add:

```temper
let d = new Deque<Int>();
// ❌ Deque has not been declared; it's std's Queue now: add `let { Queue } = import("std/collections");`
```

Source: [collections.temper.md](../../lib/std/collections/collections.temper.md).
