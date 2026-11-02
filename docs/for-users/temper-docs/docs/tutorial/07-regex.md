---
title: Regular Expressions
temper-extract: false
---

# Regular Expressions

A regular expression (or regex or regexp for short), usually refers to a pattern
for matching and/or replacing text. The goal is convenience, and sometimes speed
when in a runtime where ordinary code evaluation is slow. Both can be useful for
Temper-built libraries as well.


## Structured, portable regex

Temper regex support is designed for compatibility across backends, even though
a variety of different regex engines might be used. As a simple example, here's
a named capture in JS:

```js
// JS
> /a(?<thing>.)c/.exec("abc").groups.thing
'b'
```

And here's the same in Python:

```py
# Python
In [2]: re.match(r"a(?P<thing>.)c", "abc").group("thing")
Out[2]: 'b'
```

Specifically, there's a `P` after the `?` in Python, but there isn't in JS.
That's one example among many for differences in regex between JS and Python.
Java has its own differences. Temper can't just forward regex source to backend
engines and expect consistent behavior. And implementing or bringing in a full
third-party regex engine might be ok for some backends but not others. For
example, most JS developers want to keep dependencies small.

Because of this, Temper internally represents regexes in structured form. We
then translate a regex tree into a backend representation matching the common
semantics. Let's start with the above example in Temper's custom dialect (and
see [issue#26](https://github.com/temperlang/temper/issues/26) for reorganizing `std`):

```temper
// Import regex module definitions into the current scope.
// Import `...` only for well-known libraries.
// Also, the name for the regex library will likely change in the future.
$ let { ... } = import("std/regex");
interactive#0: void
$ let regex = /a(?thing=.)c/;
interactive#1: void
$ regex.find("abc")["thing"].value
interactive#2: "b"
```

From this regex, we can also get its structured representation:

```temper
$ regex.data
interactive#3: {class: Sequence, items: [{class: CodePoints, value: "a"},{class: Capture, name: "thing", item: {class: Dot__13}},{class: CodePoints, value: "c"}]}
```

Or we can manually build an equivalent:

```temper
$ let regex2 = new Sequence([
    new CodePoints("a"),
    { class: Capture, name: "thing", item: Dot },
    new CodePoints("c"),
  ]).compiled();
interactive#4: void
$ regex2.find("abc")["thing"].value
interactive#5: "b"
```

The above tree translates to the correct syntax on each backend. Currently, this
happens only at run time, but in the future, we expect to convert compile-time
constant regex patterns during compilation to each backend (see [issue#11](https://github.com/temperlang/temper/issues/11)).
This will reduce the output code size needed for running.


## Regex interpolation

For a different form of regex literals, we also support interpolating escaped
string values. Regex literals allowing this are formed with a tagged string:

```temper
// Different meaning than before because of interpolated string escaping!
$ let text = ".";
interactive#6: void
$ let otherRegex = rgx"a(?thing=${text})c";
interactive#7: void
// Note {class: CodePoints, value: "."} rather than {class: Dot__13}.
$ otherRegex.data
interactive#8: {class: Sequence, items: [{class: CodePoints, value: "a"},{class: Capture, name: "thing", item: {class: CodePoints, value: "."}},{class: CodePoints, value: "c"}]}
```

All these would be equivalent:

```temper
let otherRegex = rgx"a(?thing=${"."})c";
let otherRegex = rgx"a(?thing=\.)c";
let otherRegex = /a(?thing=\.)c/;
```

You can also manually compose regexes into larger wholes. All Temper match
groups are named so that composition makes more sense:

```temper
// This embeds the previous pattern into a new larger one.
// Nested recursive structures are allowed here because these types are
// immutable. No cycles are possible.
let regex3 = new Sequence([
  { class: Repeat, item: regex.data, min: 0, max: 1 },
  new CodePoints("d"),
]).compiled();
```

To make this easier, we also plan to support interpolating regex values into
`rgx` literals (see [issue#33](https://github.com/temperlang/temper/issues/33)):

```temper
// Would provide the same embedded regex as the previous example.
// NOTE! This isn't yet supported.
let regex3 = rgx"${regex}?d";
```

See [*regex.temper.md*](https://github.com/temperlang/temper/blob/main/frontend/src/commonMain/resources/std/regex/regex.temper.md) in the Temper source for the full current schema
and features. We plan to extend this in the future (see [issue#9](https://github.com/temperlang/temper/issues/9), [issue#16](https://github.com/temperlang/temper/issues/16),
and more). Also see [*match.temper.md*](https://github.com/temperlang/temper/blob/main/functional-test-suite/src/commonMain/resources/regex/match/match.temper.md) for more examples of
using the features.

Again, for regex values that are stable at compile time, we plan to support
compiling them to backend representations at compile time as well (see
[issue#11](https://github.com/temperlang/temper/issues/11)). You should even be able to use pure functions to programmatically
build up patterns during compilation.

We might also move toward insignificant whitespace in the default dialect.


## Unicode

As Temper regex support gets built out, we expect to keep Unicode in mind. For
example, `Dot` (as seen above, or as `.` in regex source text) always means a
full Unicode code point in Temper. We also plan to support Unicode character
class references (such as `\p{L}` for "letters" across all scripts, see
[issue#9](https://github.com/temperlang/temper/issues/9)). This is at the planning stage only, but we're hopeful we can work
out useful semantics.


## Closing words

That concludes our quick preview into Temper regular expressions for now. We
plan for them to be something anyone can feel comfortable reaching for with any
backend (see also [issue#14](https://github.com/temperlang/temper/issues/14)).

And this also concludes our introductory tutorial for Temper at this time. We'll
fill out more as the language also gets filled out. But for now, we hope you are
able to start using Temper to develop idiomatic, cross-language libraries.

We're glad you're here!


## Links
- **PREVIOUS**: [Memory Management](06-memory.md)
- Reference: [Builtins](../reference/builtins.md),
  [Types](../reference/types.md)
