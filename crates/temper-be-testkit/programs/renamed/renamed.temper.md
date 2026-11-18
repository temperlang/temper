# Renamed

Exports whose names backends' namings change, each for a keyword or a
builtin of some target: a function, a `let`, and a class with a field,
a method, and a getter. And a class whose members are named like
builtins, which members keep, as Kotlin's (JS `get` and `set`, Python
`hash`, `map`, and `format`), but for what Python prints in a class
body: `int`, which the later `-> int` would read. `init` and `print`
are also names Rust and Lua print, but not on members. `imports-library` uses them, spelling each as
this library's own naming spelled it, from its interface, never
deriving it again (notes/tmpl/open-questions.md, 46).

    export let lambda(n: Int): Int { n + 1 }
    export let native = 2;

    export class Checked(public end: Int) {
      public delete(): Int { end * 2 }
      public get local(): Int { end + native }
    }

    export class Builtins(public hash: Int) {
      public get(i: Int): Int { hash + i }
      public set(i: Int): Int { i * 2 }
      public map(i: Int): Int { i + 1 }
      public format(): String { "#${hash.toString()}" }
      public get int(): Int { hash * 10 }
      public later(): Int { int + 1 }
      public init(): Int { hash + 3 }
      public print(): String { "print ${init().toString()}" }
    }
