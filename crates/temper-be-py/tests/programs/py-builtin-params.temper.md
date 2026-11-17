# Parameters named like Python's builtins

Python's naming keeps a parameter's name, and a local's, unless it's a
keyword: `help`, `min`, `max`, `type`, and `len` stay as they are, so a
constructor's parameters, which Python callers may pass by name, are
`Option(help='…')`, never `help_=`. Where a function's own names hide a
builtin that its code calls or annotates with, it reaches the builtin
through the `builtins` module instead; elsewhere builtins stay bare.

A constructor and a method whose parameters hide no builtin they use,
and a class implementing the interface that `isOption`, below, tests:

    export interface Described {
      public describe(): String;
    }

    export class Option(
      public help: String,
      public min: Int,
      public max: Int,
      public type: String,
    ) extends Described {
      public clamp(len: Int): Int { len.max(min).min(max) }
      public describe(): String { "${type}: ${help}" }
    }

    class Plain extends Described {
      public describe(): String { "plain" }
    }

    let o = new Option("how", 1, 9, "flag");
    console.log("${o.clamp(12).toString()} ${o.clamp(0).toString()} ${o.describe()}");

```log
9 1 flag: how
```

Functions whose parameters and locals hide what their bodies use: `len`
for a list's length, `max` and `min` for `Int`'s, also in a closure and
with a default, `int` for a local's annotation, `str` for `toString`, and
`isinstance` for `is`.

    export let total(len: Int, items: List<String>): Int { items.length + len }
    export let widest(min: Int, max: Int, sizes: List<Int>): Int {
      sizes.reduceFrom(min) { (a: Int, b: Int): Int => a.max(b) }.min(max)
    }
    export let limited(n: Int, max: Int = 10): Int { n.min(max) }
    export let doubled(int: Int): List<Int> {
      let twice = new ListBuilder<Int>();
      twice.add(int * 2);
      twice.toList()
    }
    export let shown(str: Int): String { str.toString() }
    export let isOption(isinstance: Described): Boolean { isinstance is Option }
    export let counted(items: List<String>): Int {
      let len = items.length;
      len * 2
    }

    console.log(total(2, ["a", "b", "c"]).toString());
    console.log(widest(0, 10, [3, 12, 5]).toString());
    console.log("${limited(12).toString()} ${limited(12, 4).toString()}");
    console.log(doubled(21).join(",") { (i: Int): String => i.toString() });
    console.log(shown(7));
    console.log("${isOption(o)} ${isOption(new Plain())}");
    console.log(counted(["a", "b"]).toString());

```log
5
10
10 4
42
7
true false
4
```

A parameter named for a keyword still takes a trailing `_`, as soft
keywords don't (`type`, above):

    export let span(from: Int, lambda: Int, class: Int): Int { lambda - from + class }

    console.log(span(3, 10, 0).toString());

```log
7
```
