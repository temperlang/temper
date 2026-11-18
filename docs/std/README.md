# The standard library

Temper's standard library, `std`, comes with Temper, and every backend
supports it. Each of its modules is imported by path:

```temper
let { Date } = import("std/temporal");

console.log(new Date(2000, 12, 31).toString()); //!outputs "2000-12-31"
// ✅
```

| Module | What it's for |
|---|---|
| [`std/json`](json.md) | Converting values to and from JSON, with `@json` |
| [`std/regex`](regex.md) | Regular expressions, from literals or built as data |
| [`std/temporal`](temporal.md) | Calendar dates |
| [`std/testing`](testing.md) | What `test` blocks run on |
| [`std/net`](net.md) | Simple HTTP requests |

What's always available without an import, such as `String`, `List`,
`Map`, and `console`, is in the language reference:
[types and values](../language/types.md) and
[builtins](../language/builtins.md).

The library is written in literate Temper, so its sources are its
fullest documentation; `temper` builds them in, from
[lib/std/](../../lib/std/config.temper.md). On each
backend, some of it connects to the language's own types and
functions (`Date` is a `java.time.LocalDate` in Java, for example);
[`@connected`](../language/builtins.md#connected-decorator) marks those.

These pages are short summaries, written for this repository from the
sources; the old documentation site had no `std` reference beyond the
[regular expressions tutorial](../guide/tutorial/07-regex.md).
