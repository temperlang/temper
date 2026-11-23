# Empty generic values where nothing pins their type arguments

A generic class's instance made with no arguments, as std's `Queue` or a
class of a library's own, gets its type arguments from where it goes:
an annotated local, a parameter, or a return. Where it goes to a type
that doesn't name them, as `AnyValue` or a generic function's type
parameter, checkers would see them as unknown (`deque[Unknown]`), so
the call names them: `deque[int]()`.

    let { Queue } = import("std/collections");

    class Holder<T> {
      public var items: List<T> = [];
    }

    class Marker {}

    let kind(x: AnyValue): String {
      if (x is Marker) { "marker" } else { "other" }
    }

    let same<T>(x: T): T { x }

    console.log(kind(new Holder<Int>()));
    console.log(kind(new Queue<String>()));
    console.log(kind(new MapBuilder<String, Int>()));
    console.log(kind(same(new Queue<Int>())));
    console.log(same(new Holder<String>()).items.length.toString());

```log
other
other
other
other
0
```
