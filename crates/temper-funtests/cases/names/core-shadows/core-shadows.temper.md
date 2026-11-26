# Core Names Shadowed Functional Test

A module may declare names core declares too, as `String`, `List`, or
`ignore`, and its own declarations are what those names mean there.

    let { summary } = import("./mine");
    console.log(summary().toString());

Other modules still see core's:

    let s: String = "core's string";
    console.log(s);
    let xs: List<Int> = [1, 2];
    console.log(xs.length.toString());

And a local may shadow a core name, as `console`:

    let bump(console: Int): Int { console + 1 }
    console.log(bump(5).toString());

Expected output:

```log
31
core's string
2
6
```
