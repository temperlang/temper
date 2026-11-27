# Type parameters named once

A function's type parameter that its signature names only once, in its
result, as `List<T>` or `T?`, is a `TypeVar` there all the same, so a
call's expected type solves it (`emptyOf<Int>()` as `Sequence[int]`).
pyright's strict `reportInvalidTypeVarUse` reports it, so such a `def`
says `# pyright: ignore[reportInvalidTypeVarUse]`, merged with any other
rule ignored there; and a result of just `T`, which mypy also reports,
says `# type: ignore[type-var]`, which pyright reads too.

    export let emptyOf<T>(): List<T> { [] }
    export let nothing<T>(): T? { null }
    export let failing<T>(): T throws Bubble { bubble() }
    let halved<A, B>(a: A): Pair<A, List<B>> { new Pair<A, List<B>>(a, []) }

    export class Maker {
      public pairOf<B>(n: Int): Pair<Int, List<B>> { new Pair<Int, List<B>>(n, []) }
      public static none<B>(): List<B> { [] }
    }

    let ints = emptyOf<Int>();
    let none = nothing<String>();
    let half = halved<Int, String>(2);
    let pair = new Maker().pairOf<String>(3);
    let made = Maker.none<Int>();
    console.log("${ints.length} ${none ?? "-"} ${failing<Int>() orelse 4}");
    console.log("${half.key} ${pair.key} ${pair.value.length} ${made.length}");

```log
0 - 4
2 3 0 0
```
