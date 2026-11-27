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

Locals that share a name, as Python's one scope per function lets them
where neither is alive while the other is and their types match, with
or without null; and a `var` assigned again. mypy gives a name the
type of its first binding, and rejects a later annotation of it, so
only that binding is annotated, with the widest type, and only where
its value may be narrower: a tuple than a `Sequence`, a class than the
interface it implements, a list literal than a list of another length,
or a value than the nullable type. A walrus that binds such a name
first, for `??`, is declared just before, if its value may be
narrower. A cast's local is annotated too, as pyright takes
`cast_by_type` to give its argument's type, the wider; and
so is a builder made from a list literal, which pyright takes for a
list of its items' literal types (`list[Literal[0]]`).

    let maybe(s: String): String? { if (s.isEmpty) { null } else { s } }
    export let joined(firsts: ListBuilder<String>, seconds: List<String>): String {
      let out = new StringBuilder();
      for (let s of firsts.toList()) { out.append(s); }
      for (let s of seconds.slice(0, seconds.length)) { out.append(s); }
      out.toString()
    }
    export let repeated(text: String, times: Int): String {
      var sbOrNull: StringBuilder? = null;
      for (var i = 0; i < times; ++i) {
        let sbNow = sbOrNull;
        let sb = sbNow ?? new StringBuilder();
        sbOrNull = sb;
        sb.append(text);
      }
      let sb = sbOrNull;
      if (sb != null) { sb.toString() } else { "-" }
    }
    export let found(a: String, b: String): String {
      let first = maybe(a) ?? maybe(b);
      if (first != null) { return first; }
      if (a == b) { "same" } else { maybe(b) ?? "none" }
    }
    export let last(sides: List<Int>): Described {
      var at: Described = new Plain();
      for (let n of sides) { at = new Option("sides", n, n, "shape"); }
      at
    }
    export let resized(more: Boolean): Int {
      var sizes = [1, 2];
      if (more) { sizes = [3]; }
      sizes.length
    }
    export sealed interface OptionError extends Error {}
    export class NotAnOption extends OptionError {}
    export let helpOf(d: Described): String throws NotAnOption {
      let option = if (d is Option) { d } else { throw new NotAnOption() };
      option.help
    }
    export let pieces(n: Int): List<Int> {
      let pieces = [0, 0].toListBuilder();
      pieces[1] = n;
      pieces.toList()
    }
    export let either(subject: String): String {
      console.log(subject);
      let r = maybe(subject.slice(String.begin, String.begin)) ?? "empty";
      r
    }

    let more = new ListBuilder<String>();
    more.add("a");
    more.add("b");
    console.log(joined(more, ["c"]));
    console.log("${repeated("ab", 2)} ${repeated("ab", 0)}");
    console.log("${found("", "x")} ${found("", "")} ${found("y", "")}");
    console.log("${last([]).describe()} ${last([4]).describe()}");
    console.log("${resized(false).toString()} ${resized(true).toString()}");
    console.log("${helpOf(o) orelse "-"} ${helpOf(new Plain()) orelse "-"}");
    console.log(pieces(7).join(",") { (i: Int): String => i.toString() });
    console.log(either("e"));

```log
abc
abab -
x same y
plain shape: sides
2 1
how -
0,7
e
empty
```
