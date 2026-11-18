# Inconsistencies

Where Temper's documentation disagrees with itself, with temperlang.dev,
with Temper's previous compiler (written in Kotlin), or with this one, found
while gathering the docs here (2026-11-07). Each is flagged in place
with a `> **Note:**` that links here. Resolve one by fixing the docs,
the compiler, or both; then remove its note and its entry.

Where this compiler differs from the previous one on purpose, or works
around its bugs, is in
[notes/kotlin-differences.md](../notes/kotlin-differences.md); this
page doesn't repeat those.

| | Where | In short | Suggested |
|---|---|---|---|
| [I1](#i1-the-documentations-license) | site, COPYRIGHT | Docs license: BY-NC-SA or BY-SA | Fix the site's footer |
| [I2](#i2-discord-invites) | READMEs, tour, site | Three Discord invites | Pick one permanent invite |
| [I3](#i3-c-support) | README, target languages | C++ "coming soon" | Say C++ is supported |
| [I4](#i4-backends-not-available-yet) | target languages, builtins, CLI | `java8`, `mypyc`, `data`, `cppv` | Decide which are needed |
| [I5](#i5-node-and-javascript-versions) | getting started, release notes, target languages | Node 14 or 18; ES2018 | State one supported range |
| [I6](#i6-the-temperout-layout) | getting started | Old output paths | Done here; check the site |
| [I8](#i8-numbered-names-in-output-samples) | home page | `x__4` in samples | Done here |
| [I9](#i9-the-command-line) | guide, tools | `temper` commands not available yet | CLI parity |
| [I10](#i10-examples-that-dont-run-as-marked) | builtins, types | 9 examples | Fix the compiler, one by one |
| [I11](#i11-playground-addresses) | terms, previous README | Old playground addresses | Links done here; terms left |
| [I12](#i12-the-functional-test-matrix) | `functional-test-matrix.md` | Failures this compiler doesn't have | Regenerate, or retire |
| [I13](#i13-lua-versions) | target languages | "Lua 5.1" | State what's tested |
| [I14](#i14-python-named-arguments) | target languages | Named arguments | Done here |
| [I15](#i15-contributor-toolchains) | CONTRIBUTING | Toolchain versions | Keep one list per repository |
| [I16](#i16-a-function-expression-used-as-a-declaration) | modules | `fn greet` then `greet()` | Done here |
| [I17](#i17-the-tours-date-snippets) | tour | `new Date(…)` in each language | Use each language's date |
| [I18](#i18-for--of-loops) | tutorial 3 | "for each" is future work | Teach `for … of` |
| [I19](#i19-module-prefaces-and-) | modules | Prefaces and `;;;` | Document what exists |
| [I20](#i20-top-level-statements-run-out-of-order) | builtins | Logs in source order | Settle top-level order |
| [I21](#i21-dateyearsbetween-with-the-later-date-first) | std/temporal | Negative, off by one | Fix std, or document |
| [I22](#i22-named-groups-in-regular-expressions) | std/regex | `(?<name>…)` | Pick one syntax, or both |
| [I23](#i23-integer-literals-too-big-for-int32) | builtins | Four bit examples wrap | Decide literal typing, then fix |
| [I24](#i24-tutorial-examples-that-dont-run) | tutorial 3 | `fn` without a declared type; `do(fn …)` | Fix the examples, or the compiler |

## Licensing and community

### I1: The documentation's license

- **Docs:** [COPYRIGHT](../COPYRIGHT), in both repositories, and
  `temper license` say the documentation is CC BY-SA 4.0.
- **Site:** temperlang.dev's footer, from its `mkdocs.yml`, links CC
  BY-NC-SA 4.0 (non-commercial).
- **Suggested:** BY-SA, as COPYRIGHT says; fix the footer. These docs
  follow COPYRIGHT ([NOTICE](../NOTICE)).

### I2: Discord invites

- **Docs:** the previous compiler's README and CONTRIBUTING link
  `discord.gg/QQKgZMukVB`, the site's footer `discord.gg/TY3Be6fzwu`,
  and the tour `discord.com/invite/6Jb83cq5FH`.
- **Here:** every page uses the site's, `TY3Be6fzwu`.
- **Suggested:** check which still work, make one permanent, and use it
  everywhere.

### I11: Playground addresses

- **Here:** every link to try Temper (the README, the guide, and
  [tools](tools/playground.md)) goes to this compiler's playground, published
  on GitHub Pages with the docs site. `play.temperlang.dev` is left
  only where it's history: the internals' comparison with Temper's
  first playground.
- **Left:** the [playground terms](legal/playground-terms.md) still
  name `playground.temper.systems`, the old site's playground, and the
  previous compiler's README still links `play.temperlang.dev`.
- **This compiler:** a static page, compiling in the browser, with no server
  and no accounts; sharing puts the code in the URL.
- **Suggested:** terms that fit what it does (a static page may need few
  or none), the previous README's link updated, and one lasting address.

## Languages and versions

### I3: C++ support

- **Docs:** the previous README lists C++ under "Coming soon"; the target
  language list leaves it out; the tour lists six languages.
- **This compiler:** C++ passes every functional test, and real
  libraries' tests, under sanitizers.
- **The previous compiler:** has a `cpp` backend (not built by default), failing some
  functional tests.
- **Suggested:** list C++ as supported, built with `-b cpp`; done in
  [target languages](backends/README.md) and the tour.

### I4: Backends not available yet

- **Docs:** the target language list documents `java8`, `mypyc`, and
  `data`; builtins document `dataFile`, which writes through `data`;
  `temper build` builds `data` by default; the test matrix has `cppv`.
- **This compiler:** none of these; `dataFile` isn't declared.
- **Suggested:** decide which matter. Java 8 has users in older JVMs;
  `mypyc` and `cppv` are experiments; `data` needs `dataFile`.

### I5: Node and JavaScript versions

- **Docs:** getting started says Node 14 or later; the 0.x release note
  "Require Node 18 and Python 3.11" says 18; the target language list
  says ES2018.
- **This compiler:** CI runs generated JavaScript on Node 24, and checks it
  with the latest TypeScript.
- **Suggested:** state one tested range, such as Node 18 and later, and
  test the oldest in CI.

### I13: Lua versions

- **Docs:** "Supported version: Lua 5.1".
- **This compiler:** rockspecs declare `lua >= 5.1`; tests run on Lua 5.4 and
  LuaJIT, not PUC Lua 5.1; a few functional tests need 5.3 or later.
- **Suggested:** say "5.1 and later, tested on 5.4 and LuaJIT", and
  list what needs 5.3.

### I14: Python named arguments

- **Docs:** the Python notes said named arguments translate to Python's,
  but with numeric suffixes still to remove.
- **Both compilers:** named arguments were removed from Temper (see the
  release notes); this compiler writes no numbered names.
- **Here:** the note is replaced by how names translate.

### I15: Contributor toolchains

- **Docs:** the previous CONTRIBUTING asks for JDK 21, Java 8 and 17,
  Maven, Lua 5.1 or 5.4, .NET 6, Node 18, Python 3.11, and Rust 1.71.1.
- **This compiler:** Rust 1.85.0, uv, and each language's tools to run every
  backend's tests ([contributing](contributing/README.md#testing)).
- **Suggested:** each repository keeps its own list; no change needed
  until the previous compiler's repository retires.

## Output and tools

### I6: The `temper.out` layout

- **Docs:** getting started listed `csharp/hello/src/Hello/HelloGlobal.cs`,
  `java/hello/src/main/java/hello/hello/HelloGlobal.java`, and
  `lua/hello/hello.lua`.
- **This compiler:** `src/HelloGlobal.cs`,
  `src/main/java/hello/HelloGlobal.java`, and `init.lua`; its layout is
  tested against the previous compiler's current one.
- **Here:** the list shows this compiler's paths.

### I8: Numbered names in output samples

- **Docs:** the home page's translations, made by the previous compiler,
  had names like `x__4` and `this__0`.
- **Here:** the [introduction](guide/introduction.md) shows this compiler's
  translations, regenerated, with the source's names.

### I9: The command line

- **Docs:** the guide uses `temper init`, `build`, `test`, `watch`,
  `repl` (and its `describe`), `version`, and `help`; the language
  server is `temper serve --port N`.
- **This compiler:** `temper`, with the same commands and flags, as
  `temper build`, `test`, `run`, `repl`, `init`, `version`, `license`,
  `licenses`, `help`, and `serve` (or `lsp`, on standard input and
  output only); no `watch`, `doc`, or `repl -b` yet, which say so; the
  REPL's `describe` takes this compiler's steps, not the previous stage
  names ([the command line](tools/cli.md), [the REPL](tools/repl.md)).
- **Suggested:** finish CLI parity.

### I12: The functional test matrix

- **Docs:** the previous compiler's `functional-test-matrix.md`,
  generated, shows failures on `cpp`, `cppv`, `lua`, and others.
- **This compiler:** all 72 functional tests pass on the interpreter and on
  every backend.
- **Suggested:** not imported here; regenerate it there, or retire it
  when this compiler takes over.

## Language and examples

### I10: Examples that don't run as marked

The reference's examples carry the result the previous compiler gave
when its docs were generated, and `compat/check_doc_examples.py` runs
them with this one ([writing these docs](contributing/docs.md#examples-that-are-checked)).
These 9 disagree; each was checked on the previous compiler, which runs
them as marked. Each has a `<!-- known: I10 -->` comment before its
fence, so the checker, which runs in CI, counts it as known.

| Where | Example | This compiler |
|---|---|---|
| [builtins](language/builtins.md#cat) | `cat("foo", "bar")` | "cat has not been declared" |
| [builtins](language/builtins.md#macro-datafile) | `dataFile(…)` | "dataFile has not been declared" (see [I4](#i4-backends-not-available-yet)) |
| [builtins](language/builtins.md#defining-functions) | A named function expression, `fn f(): String { … }` | "Use of builtin fn is malformed" |
| [builtins](language/builtins.md#inlineunrealizedgoal-decorator) | `return` from a block lambda passed to `forEach`, inlined | "Expected subtype of Void, but got Boolean" |
| [builtins](language/builtins.md#builtins-keyword) | Subclassing `Console`; `builtins.console` | "Console has not been declared" |
| [types](language/types.md#float64-syntax-examples) | `1.` is an error | Accepted, as `1` and a dot |
| [builtins](language/builtins.md#assignment--builtin), [`++`](language/builtins.md#-operator-increment), and [`--`](language/builtins.md#---operator-decrement) | Logging a top-level `var` around assignments | See [I20](#i20-top-level-statements-run-out-of-order); both compilers agree |

**Suggested:** fix the compiler for each, or record a deliberate difference
in [notes/kotlin-differences.md](../notes/kotlin-differences.md), and
remove its `known` comment and its row here.

### I16: A function expression used as a declaration

- **Docs:** the literate example in [modules](language/modules.md)
  declared `fn greet(…) { … }` and later called `greet("world")`; but
  a function expression's name isn't visible outside it, as
  [builtins](language/builtins.md#fn) explain.
- **Here:** it's `let greet(…)`.

### I17: The tour's date snippets

- **Docs:** the tour's `Person` uses `Date`, without an import, and its
  C#, Java, JavaScript, Lua, and Python snippets construct
  `new Date(1987, 6, 5)` from the library.
- **Both compilers:** `std/temporal`'s `Date` is each language's own
  date type (`DateTime`, `LocalDate`, JavaScript's `Date`, whose months
  count from 0, and `datetime.date`).
- **Suggested:** import `std/temporal` in the Temper code, and use each
  language's date in the snippets.

### I18: `for … of` loops

- **Docs:** tutorial 3 says "for each" looping is future work
  ([issue 20](https://github.com/temperlang/temper/issues/20)).
- **Both compilers:** `for (let x of list) { … }` works, with `break`
  and `continue`, since 0.4.0 (see the release notes).
- **Suggested:** teach `for … of` in tutorial 3.

### I19: Module prefaces and `;;;`

- **Docs:** [modules](language/modules.md) describes a module
  *preface*, module *parameters*, and up to two `;;;` tokens: preface,
  body, then JSON metadata.
- **Both compilers:** one `;;;` separates code from JSON metadata
  after it (this compiler follows the previous one's
  `splitCodeFromMetadata`); there are no prefaces or parameters
  ([issue 10](https://github.com/temperlang/temper/issues/10)).
- **Suggested:** document `;;;` as it works, and keep prefaces as a plan.

### I20: Top-level statements run out of order

- **Docs:** three [builtins](language/builtins.md#assignment--builtin)
  examples log a top-level `var` before and after assigning it, as
  source order would.
- **Both compilers:** log the value after every assignment, twice,
  since a module's top-level readers of a `var` wait for every
  statement that assigns it; inside a function, they log as shown.
- **Suggested:** settle top-level order, which
  [notes/kotlin-differences.md](../notes/kotlin-differences.md) ("Top-level
  statements in source order") calls broken; until then, move the
  examples into a function.

### I21: `Date.yearsBetween` with the later date first

- **Docs:** `std/temporal`'s doc comment says the result is the floor
  of the range's magnitude, so the order of the dates shouldn't matter.
- **Both compilers' interpreters:** `Date.yearsBetween(2026-03-01,
  2024-02-29)` is `-3`, though the other order gives `2`.
- **Suggested:** fix std to match its comment (and check each backend's
  connected version, such as Java's `ChronoUnit.YEARS.between`), or
  document it as signed.

### I22: Named groups in regular expressions

- **Docs:** std's regex docs write a capture as `/(?<name>...)/` and,
  a few lines later, `/(?name = ...)/`; the tutorial uses `(?name=…)`.
- **The previous compiler:** accepts both.
- **This compiler:** accepts only `(?name=…)`: `lookaround and (?<name>...)
  aren't supported; use (?name=pattern)`.
- **Suggested:** accept `(?<name>…)` here too, since JavaScript,
  Java, C#, and Python 3.11's `re` (as `(?P<name>…)`) users expect it.

### I23: Integer literals too big for Int32

- **Docs:** four [builtins](language/builtins.md#operator--shift-right)
  examples, `>>`, `>>>`, `^`, and `~`, write 64-bit hex or 32-bit binary
  literals without `i64`, such as `0x8000_0000_0000_0000` and
  `0b1111_0000_…`.
- **Both compilers:** read them as `Int32`, warn that they're out of
  bounds, and wrap them (hex up to `0xFFFF_FFFF` is exempt, as bits;
  binary isn't). The `^` and `~` examples still hold after wrapping; the
  shifts compare wrapped values.
- **Suggested:** decide whether integer literals take their type from
  context; then add `i64`, or write the 32-bit ones in hex, as the
  answer needs.

### I24: Tutorial examples that don't run

The tutorial's REPL transcripts run as a test
(`crates/temper-frontend/tests/repl_docs.rs`); two of
[control flow](guide/tutorial/03-control-flow.md)'s examples are left
out of it, as this compiler rejects them:

- `let areaPerPerimeter = fn (shape: Shape): Float64 { … }`: "needs a
  declared type, such as a @fun interface, since a function literal
  takes its type from the type wanted"
  ([language.md](../notes/language.md), fun interfaces).
- `do(fn () { … while(i < n, fn { … }); })`: "Use of builtin do is
  malformed".

**Suggested:** decide whether the compiler should accept them, or show
what it accepts in the tutorial.
