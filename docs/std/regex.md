# `std/regex`: regular expressions

```temper
let { Regex, Match, Group } = import("std/regex");
```

Temper has its own regular expression dialect, which every backend
runs the same way. Write a literal, `/…/`, or build a pattern as data
from `RegexNode`s; either way, it compiles to a `Regex`:

```temper
let { Regex } = import("std/regex");

let word = /(?first=[a-z]+)-(?second=[a-z]+)/;
let m = word.find("well-known") orelse panic();
console.log(m.groups["second"].value); //!outputs "known"
console.log(word.replace("hi-there") { m => "[${m.full.value}]" }); //!outputs "[hi-there]"
// ✅
```

The [regular expressions tutorial](../guide/tutorial/07-regex.md) walks
through the dialect and the data model. A named group is `(?name=…)`.

> **Note:** Temper's earlier releases also accept `(?<name>…)`, as the
> std's own docs write it; now it says `lookaround and (?<name>...)
> aren't supported; use (?name=pattern)`
> ([inconsistencies](../inconsistencies.md#i22-named-groups-in-regular-expressions)).

| Member | What it does |
|---|---|
| `found(text)` | Whether the pattern matches anywhere in `text` |
| `find(text, begin)` | The first `Match` from `begin`, or bubbles if none |
| `replace(text, format)` | Replaces each match with `format(match)` |
| `split(text)` | The parts between matches |
| `Match.full`, `Match.groups` | The whole match, and named groups, each a `Group` |
| `Group.name`, `value`, `begin`, `end` | A group's name, text, and where it is |

To build patterns as data, `RegexNode` has the kinds of node:
`Sequence`, `Or`, `Repeat` (with `oneOrMore`, `optional`, and
`entire`), `Capture`, `CodePoints`, `CodeRange`, and `CodeSet`, and the
specials `Begin`, `End`, `Dot`, `WordBoundary`, `Digit`, `Space`, and
`Word`. Any of them `compiled()` is a `Regex`. Character classes and
Unicode properties aren't supported yet.

Source: [regex.temper.md](../../lib/std/regex/regex.temper.md).
