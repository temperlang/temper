# Size and speed

Part of the [rewrite plan](README.md).

## 12. Size: today vs. target

Today, from the source survey (Kotlin, main code, not tests; approximate):

| Area | Today (hand-written) | Plus generated |
|---|---|---|
| Lexer, parser, CST/AST building | ~23k | |
| Frontend, fundamentals, builtins, interpreter, names | ~92k | |
| Backend shared layer (TmpL translation etc.) | ~18k | ~12k (TmpL classes) |
| Seven backends | ~52k | ~42k (per-language ASTs) |
| CLI, build/watch, LSP, logging, files, common | ~38k | |
| Code generator for the grammars (kcodegen) | ~7k | |
| **Total** | **~230–240k** | **~54k** |

Plus roughly 70k lines of Kotlin unit tests.

Target in Rust:

| Area | Target | Why smaller |
|---|---|---|
| Literate, lexer, parser into the tree | 4–6k | One table-driven parser straight into the tree; no separate CST, AST or grammar DSL. (The literate part is done: ~650 lines.) |
| Tree, settle loop, names, macros, types, interpreter, core functions | 20–30k | One representation instead of several; no stage machinery or re-walks; one type system instead of two plus bridges |
| tmpl, shared lowerings and naming | 4–7k | Small, specified IR; no frontend types; lowering done once for all backends |
| Seven backends | 21–35k | Printers via a shared document builder; no generated ASTs |
| CLI, build/watch, LSP, logging | 8–12k | Arenas per build make caching and cleanup simple; no JVM concerns |
| **Total** | **~60–90k** | **About 3× smaller (4× counting generated code)** |

The biggest uncertainty is the long tail of type-system and macro semantics
that the 69 functional tests don't pin down. If it pushes, the frontend could
grow toward the top of its range, but I don't expect past ~100k total.

## 13. Speed and memory

What we measured on Prismora (now Chromarite), at ~900 lines:

- `temper watch` rebuild plus Python tests: 2–5 s. A one-shot
  `temper test -b py` takes ~28 s, much of it JVM startup and warmup.
- Watch process: ~400 MB RSS, and ~40 MB live heap after GC once the leak
  is fixed.
- One build holds ~80k edge objects plus as many nodes, positions, values and
  type-inference records, and hundreds of thousands of map entries.

Where Rust gets its speed, roughly in order of impact:

1. **Far fewer allocations.** A build is a few growing arrays instead of
   hundreds of thousands of small objects. This is the biggest win: less
   allocator work, no garbage collection, and data that stays in cache.
2. **No JVM start or warmup.** Milliseconds instead of seconds to start, which
   matters most for one-shot `build`/`test` and the language server.
3. **No repeated whole-tree passes.** The settle loop only revisits what was
   waiting on something that changed.
4. **Interned names and types**, so comparisons are integer comparisons and
   there's one type representation, not two plus conversions.
5. **Parallel builds** of independent libraries.

Estimates for the compiler itself (source to generated code, not running the
target language's tests):

- **Time:** 10–50× faster warm rebuilds; 50–100×+ for one-shot runs. Aim for
  under 100–200 ms for Chromarite-sized libraries.
- **Memory:** tens of MB peak for libraries this size, against ~400 MB RSS.
- **Binary:** one static binary of roughly 10–20 MB, with no JRE.

These are targets to measure against. First, time Kotlin Temper's phases on
Chromarite and the functional tests, so both sides use real numbers. And
`temper test` is often dominated by the target toolchains (Python, Maven,
cargo, dotnet), which a faster compiler doesn't change.

### When to optimize

Defer optimizations unless putting them off would force a major
architectural change later, such as data layout, how tokens or trees are
represented, or what the settle loop indexes. Measure every stage as it
lands, note ideas, and tune later.

### What will make it fast

Arenas of plain structs indexed by `u32` already work like a bump allocator,
and small nodes with interned names and types should use a fraction of
Kotlin's memory. One-shot builds lose JVM startup and warmup outright. But
the lexer shows the JIT is good at tight loops (Rust is only 2–5× warm
Kotlin there), so watch mode's big wins must come from doing less work:
waking only items waiting on a changed fact (indexed, never rescanned),
union-find over interned types, rebuilding only what changed, building
libraries in parallel, a reasonably fast compile-time interpreter for
macros, and no quadratic string building in backends. Benchmark each stage
as it lands, and profile with `perf`.

### Data

Measurements live in [size-and-speed.csv](size-and-speed.csv), one per
row; this file explains them. Columns:

- `date` and `commit`: when, and the commit measured. `tree=dirty` in
  `conditions` means that commit plus uncommitted work, the change its
  notes describe (what the old tables called "after `X`"). An empty
  commit wasn't recorded.
- `what`: a stable metric id. Sizes: `<binary>-small`, `<binary>-release`,
  `wasm-small`, and `wasm-small-gzip`; `tempest-small-no-backends`
  and `wasm-small-no-backends`, and each backend's share of the small
  `tempest` and of wasm (`backend-<name>-small`, `backend-<name>-wasm`)
  and what they share (`backends-shared-small`,
  `backends-shared-wasm`), below. Peak memory of one `--bench 1` run
  over all files: `peak-memory-parse-all` (`parse-tree`),
  `peak-memory-ast-all` (`ast-tree`), and `peak-memory-settle-all`
  (`run-temper`). Stage times: `lex-ms`, `lex-parse-ms`,
  `lex-parse-grammar-ms`, `lex-parse-module-ms`, and
  `lex-parse-module-settle-ms`. Interpreter loops, per iteration:
  `interp-loop-us`, `interp-calls-us`, `interp-closures-us`, and
  `interp-fib-us`. Kotlin's time over ours: `*-kotlin-over-rust`. Whole
  test runs: `chromarite-tests`, `ordable-tests`, and `ordable-import`.
  Build and test steps, one row per run, wall seconds (below,
  "Charts"): `funtest-<backend>-s` (a backend crate's `cargo
  test -p temper-be-<backend>`, mostly its functional tests),
  `test-<crate>-s` for the other crates, `clippy-s`,
  `build-wasm-small-s`, `build-tempest-release-s` (from clean), and
  `build-tempest-incremental-s` (after touching one frontend file);
  each with its CPU seconds, children included, as `<id>-cpu-s`.
- `value` and `value_max`: a number, or a range's low and high ends, since
  most timings on this noisy machine are ranges of medians. Plot `value`,
  the low end, which is the better guide. Approximate values (`~2×`) keep
  the number and say so in `notes`.
- `unit`: `bytes`, `KiB`, `MiB`, `ms`, `us`, `s`, or `ratio`. Older sizes
  are in KiB, as the tables had them; `measure_size.py --record` writes
  bytes.
- `conditions`: `key=value` pairs joined by `;`: `run=rust`,
  `kotlin-warm`, or `kotlin-jvm`; `runner=tempest`, `py`, or `lua`;
  `features=net` or `no-net` (none before `net` existed); `build=release`;
  `args=--module`; `files=N` when the inputs changed; `load=busy` or
  `quieter`; and `tree=dirty`.
- `notes`: short free text, such as what changed.

To record, `uv run compat/record_times.py LOG --last --record` appends
a CI-style run's step times from its log (tab separated: date, commit,
dirty, step, wall and CPU seconds, result), and `uv run
compat/record_times.py --time ID --record -- COMMAND...` times one
command, either one redrawing the charts; `uv run compat/measure_size.py --record` appends sizes and
peak memory (with `--shares`, each backend's share too); append other measurements as rows by hand. Explain here only
what rows can't say: why a number moved, how it was measured, and what to
try next, naming the commit so the text and rows meet.
`uv run compat/size_speed_table.py [WHAT ...]` prints the latest values.
The main ones, as of 2026-11-04, after std's `temporal` (sizes and memory; the timings are
older, since the machine was too busy for them to mean anything):

| What | Conditions | Value | Date | Commit |
|---|---|---|---|---|
| `tempest-small` | features=net | 5791 KiB | 2026-11-04 | `8417090`+ |
| `run-temper-small` | features=net | 3203 KiB | 2026-11-04 | `8417090`+ |
| `run-temper-small` | features=no-net | 1710 KiB | 2026-10-30 | `06a7e0a` |
| `wasm-small` |  | 3748 KiB | 2026-11-04 | `8417090`+ |
| `wasm-small-gzip` |  | 1212 KiB | 2026-11-04 | `8417090`+ |
| `peak-memory-parse-all` | run=rust | 6.2 MiB | 2026-11-04 | `8417090`+ |
| `peak-memory-ast-all` | run=rust | 9.3 MiB | 2026-11-04 | `8417090`+ |
| `peak-memory-settle-all` | run=rust;features=net | 11.9 MiB | 2026-11-04 | `8417090`+ |
| `chromarite-tests` | runner=tempest;build=release | 0.38–0.47 s | 2026-10-27 | `aa2d723` |
| `ordable-tests` | runner=lua | 9.9–12.2 s | 2026-10-27 | `3187787` |
| `ordable-tests` | runner=py | 16.5–18.9 s | 2026-10-27 | `3187787` |
| `ordable-tests` | runner=tempest | 21–22 s | 2026-10-27 | `c2dbf09` |
| `ordable-tests` | runner=tempest;build=release | 63–72 s (33 tests) | 2026-10-27 | `aa2d723` |

**2026-11-04, `8417090`+ (std's temporal; since `807c5be`+, std's
json on four more backends, came this chunk):** the small `tempest`
grew 36 KiB (5755 to 5791 KiB), wasm 31 KiB (3717 to 3748 KiB; gzipped
1202 to 1212 KiB), and `run-temper` held (3203 KiB): Java's boxing, C#'s
`Optional<T>`, and this chunk's `Date` connections in every backend,
with Kotlin's `TemporalSupport.cs` and tempest's Rust `today()` support
embedded (about 2 KB). Rust's std crate gained no dependency (no
`time`). Peak memory held (6.2, 9.3, and 11.9 MiB; Kotlin's JVM 186.1
and 244.2 MiB).

**2026-11-04, `807c5be`+ (std's json; since `21ca58d`+, std's regex on
four more backends, came std's regex on Lua and this chunk):** the small `tempest`
grew 9 KiB (5746 to 5755 KiB), wasm 13 KiB (3704 to 3717 KiB; gzipped
1198 to 1202 KiB), and `run-temper` 5 KiB: the Lua `orelse` jumps and
this chunk's translation fixes, none large; std's sources were built in
already. Peak memory held
(6.5, 9.3, and 11.6 MiB; Kotlin's JVM 191.3 and 254.5 MiB).

`2324f34`+ is that commit plus the work committed after it (other
libraries' class members and their names, `tree=dirty` in the CSV).

**What moved since 2026-10-30** (`0387dfa` plus the Lua backend, the
last sizes recorded): the small `tempest` grew from 3559 KiB to 5655 KiB
(+59%), wasm from 2101 KiB to 3607 KiB (+72%; gzipped 708 to 1169 KiB),
and `run-temper` (with `net`) to 3191 KiB. That's four days of subset
B in `to_tmpl`, tmpl, and all seven backends: classes, nullable types,
generics, function values, parameters' defaults, core's other types,
connected host code and fallbacks, maps, and labeled loops, which took
the backends from 7 functional tests to 53. Not split by backend this
time; the shares (below) are from 2026-10-30, and the next pass at size
should start there, as the backends' printers and translators are most
of the growth. **Peak memory, measured right (2026-11-03, `3d88eec`+):** 6.3 MiB to
lex and parse all 845 files, 9.5 MiB with trees, and 11.8 MiB settling
them (Kotlin on the JVM: 197.7 and 253.5 MiB). `measure_size.py` read
`ru_maxrss` from `wait4`, which on Linux also counts the image the
child had before its `exec`: a copy of the measuring Python process.
So every reading had that process's size as a floor (`/bin/true` read
11.1 MiB that way; 1.3 MiB now), which made 2026-11-03's three rows
all 20.4 MiB; they're dropped from the CSV. It now reads the binary's
own `VmHWM` at its exit, under ptrace. The older `wait4` rows
(2026-10-30's 13.2–13.3 MiB settle, say) had the same floor, from a
smaller Python, so they aren't comparable with the new ones; the JVM's
rows barely move. No timings: other jobs were busy on the machine
(a Kotlin Gradle run for most of this work), so time-based rows
would be noise.

**Where the growth since 2026-10-30 went** (backends' shares, below,
measured again 2026-11-03): each backend's share of the small `tempest`
grew three to five times, from 68–120 KiB to 264–408 KiB, together
from about 580 KiB to 2324 KiB, most of the 2108 KiB the binary grew;
the build with no backends grew 507 KiB (2683 to 3190 KiB: the
frontend's typing and `to_tmpl` work, and `tempest build`). Two causes
stand out:

- **Embedded runtimes, about 540 KiB:** since `a7eb76c` (2026-10-30,
  after the last shares), `tempest build` writes each backend's
  `temper-core` beside its output, as Kotlin does, so each backend
  carries its runtime's text (`include_str!`): Lua 127 KiB, C++ 122,
  Java 78, C# 68, Rust 58, JS 45, Python 43. That's why Lua's and
  C++'s shares grew most for their code. It's stored as is; compressed
  it would be a fraction (gzip took wasm from 3631 to 1175 KiB).
- **Translators for subset B:** the rest, about 1.2 MiB, is the
  translators' code for classes, nullables, generics, function values,
  defaults, core's other types, host code, maps, and labeled loops. By
  an unstripped release build's symbols, each backend's crate is
  228–450 KiB (Rust's the largest), and each one's few biggest
  functions are its `Translator::module` (35–46 KiB) and its class
  translation (Rust's `class` 44 KiB, C++'s `class_members` 27 KiB,
  Lua's `type_def` 23 KiB): long `match`es, with much inlined. Nothing
  looks like a big table or one generic monomorphized many times;
  `hashbrown`'s 227 KiB is the most generic code, shared.

Wasm grew less per backend (215–363 KiB) but its shared part more,
262 to 534 KiB. Not optimized yet; the embedded runtimes are the
cheapest first step, then the translators' largest functions.

### Charts

Drawn from the CSV by `uv run compat/size_speed_charts.py`, which CI checks
is current (`--check`), so rerun it after adding rows. Each point is one
commit's measurement, with its range as a whisker. Ordable's test times
also move with Ordable's own tests (its notes say how many), and its
property tests shrank on 2026-10-28, so later rows aren't comparable with
earlier ones.

![Binary sizes](charts/sizes.svg)
![Settle time](charts/settle-time.svg)
![Peak memory](charts/peak-memory.svg)
![Ordable's tests](charts/ordable-tests.svg)

The timing charts plot every run as its own point, with a centered
rolling median over each point's neighbors (two each side) as the line
and the median distance from it as a band. Single runs here share the
machine with other agents and vary by tens of percent, so read the
line, not the points; `load=` in a row's conditions says what else was
running when that's known.

![Functional tests by backend](charts/funtest-times.svg)
![Build and lint times](charts/build-times.svg)

### Measured so far

Per stage, both sides read all inputs first and then time passes over them,
Kotlin after warmup, so these compare the work itself, without file reads or
JVM startup. Inputs: all Temper files in the Temper and Prismora (now Chromarite) repos,
814–815 files, ~700 KB. Ranges are medians across alternating runs (`compat/bench_*.py`);
this machine is noisy, so the low ends are the better guide.

The rows are `lex-ms` through `lex-parse-module-settle-ms` (2026-10-22),
with Kotlin's warm times (`run=kotlin-warm`) and the ratios.

For the token change, the old and new lexer alternated directly took
18–22 ms and 8.2–9.3 ms. Of the ~57 ms for parsing, lexing is ~9 ms, the token
adapters ~12 ms and the parser ~39 ms (`compat/bench_parse.py`).

Settling adds 34–38 ms over the module step on the same 818 files
(`compat/bench_settle.py`, which alternates the two), including copying
each file's tree into a library arena. Most files stop early at classes
and imports, which aren't in the loop yet, so this will grow. There's no
Kotlin column: its stages don't map onto ours. Ideas for later: build the
grammar tree straight into the arena instead of copying; scopes find names
by scanning a `Vec`, fine for blocks but worth a map for big modules; and
finding stuck items scans all items, which only happens when the loop is
stuck. Running the passing functional tests is instant (each well under
10 ms as a process).

The module step (call joins and metadata) costs little: alternating
`ast-tree --bench` with and without `--module` gave 123 and 125 ms, within
the noise (818 files; the machine was busier than for the row above).

The grammar step (816 files by then) adds ~35–40 ms to that, about as much
as the parser, and is untuned: a straight port of Kotlin's combinators, trying `Expr`'s 27
alternatives in order with an event list that's truncated on backtracking.
Ideas: dispatch on the next part's operator instead of trying alternatives
in turn (most fail on their first lookahead, but each call costs); keep
values out of events until lift, so backtracking drops no strings; compute
doc comment contents lazily; and find the previous token for comment and
bag lookups from a table rather than scanning back.

The lexer and parser aren't tuned much either. Ideas, roughly by expected
gain: the adapters as one streaming pass instead of five passes copying
token vectors; pooled child vectors for the parser's stack; each token's
operator matches found once, shared by the adapters and the parser; no copy
of the source in the lexer and adapters; and a direct lookup for markdown
paragraph blocks instead of a scan.

### Interpreter

`compat/bench_interp.py` runs the loops in `benchmarks/interp/` at two
sizes, so the difference gives the cost per iteration without startup or
compiling. Ours runs as `run-temper` processes; Kotlin's in one JVM per
benchmark (`compat/oracle/InterpBench.java`), which builds and runs the
program round after round as `temper repl` would, without the REPL's step
quota, and drops the first 8 rounds while the JIT warms up (loop costs
fall from ~15–20 µs to a steady 6–9 µs over the first 6 or so). Runs
alternate between the sides; ranges are of per-run medians, over two
invocations of 3 runs each. The first such runs (2026-10-22, after list
builders and `for … of`) are the `interp-*` rows; later ones are below,
in prose for now.

After `when`, `?.`, `??` and Float64 (on `85b5cbb` plus that work,
2026-10-23, 3 runs per side): `loop` 0.59–0.68 µs against 4.8–9.0 µs
(8–14×), `calls` 0.96–0.99 µs against 7.8–11 µs (8–11×), `closures`
0.99–1.1 µs against 8.3–13 µs (8–14×), `fib` 340–390 µs against
2,640–3,230 µs (7–8×). Peak memory 15 MiB against 395–492 MiB.

After strings, `StringBuilder`, tags and destructuring `let` (`704dca9`),
and top-level dependency order, `~` and `>>>` on top (2026-10-23, 3 runs
per side): `loop` 0.76–0.95 µs against 7.8–8.6 µs (9–11×), `calls`
1.1–1.4 µs against 14–27 µs (10–26×), `closures` 1.1–1.4 µs against
14–24 µs (12–18×), `fib` 422–476 µs against 3,741–4,580 µs (8–10×). Both
sides ran about a quarter slower than the run before, so this looks like
machine drift rather than a change; the ratios held. Peak memory 15 MiB
against 387–492 MiB.

After modules and `import` (`f735e3d` plus that work, 2026-10-24, two
invocations of 3 runs per side, the first much noisier): `loop`
0.64–2.0 µs against 8.8–17 µs (8–21×), `calls` 0.78–2.7 µs against
12–29 µs (4–20×), `closures` 0.82–1.9 µs against 12–29 µs (11–16×),
`fib` 391–842 µs against 3,328–6,949 µs (6–13×). The quieter invocation
alone matches earlier runs (`fib` 391–412 µs, `loop` 0.64–1.2 µs), so
no change; the interpreter only gained a frame per module. Peak memory
15 MiB against 378–481 MiB.

After `Deque`, `DenseBitVector` and several declarations in one
(`0827f8a` plus that work, 2026-10-24, 3 runs per side): `loop`
0.66–0.72 µs against 7.0–7.3 µs (10–11×), `calls` 0.88–1.1 µs against
11–14 µs (13×), `closures` 1.1 µs against 11–14 µs (10–13×), `fib`
388–399 µs against 3,273–3,833 µs (8–10×): no change. A second
invocation overlapped a multi-backend `temper test` from another session
and was far noisier (`fib` 404–1,300 µs, ratios 4–20×), so it's left
out. Peak memory 15 MiB against 377–491 MiB.

After `@fun interface`, `x: T = v` fields and locals visible only after
their declaration (`6a4f4dc` plus that work, 2026-10-24, two quiet
invocations of 3 runs per side; a first one beside another session's
`temper test` ran about twice as slow and is left out): `loop`
0.52–0.60 µs against 4.3–5.9 µs (7–10×), `calls` 0.73–1.0 µs against
6.5–8.3 µs (7–9×), `closures` 0.77–1.0 µs against 7.6–11 µs (9–10×),
`fib` 293–359 µs against 2,371–2,814 µs (7–9×): no change, as the work
was all at settle time. Peak memory 15 MiB against 379–481 MiB.

After object literals, trailing blocks, `@auto("==")` and `Empty`
(`3f2c331` plus that work, 2026-10-24, two invocations of 3 runs per
side): `loop` 0.63–1.0 µs against 7.4–9.9 µs (10–13×), `calls`
0.96–1.3 µs against 11–20 µs (12–18×), `closures` 1.0–1.3 µs against
11–17 µs (10–15×), `fib` 371–438 µs against 3,305–4,443 µs (8–11×).
Alternating the old and new `run-temper` directly, 9 runs each at large
`N`, gave the same times within noise (`fib` 647–816 against 682–835 ms,
`closures` 1,070–1,119 against 1,071–1,141 ms): the call path's new
checks, for a trailing block and for objects in `cat` and `==`, cost
nothing visible. Peak memory 15 MiB against 397–488 MiB.

After `std` imports, `Map`, `Pair` and static extensions (`5ab2a49`
plus that work, 2026-10-24, two invocations of 3 runs per side): `loop`
0.54–0.61 µs against 4.4–5.1 µs (7–9×), `calls` 0.82–1.0 µs against
6.7–8.7 µs (7–9×), `closures` 0.86–1.0 µs against 7.4–9.1 µs (8–9×),
`fib` 319–367 µs against 2,197–2,726 µs (7–8×): no change, as the call
path only gained arms for new kinds of values. Peak memory 15 MiB
against 383–494 MiB. A whole `types/json-syntax-tree` run, settling
std's `json` (~1,400 lines) with the test and running it, takes
10.1–12.0 ms as a process (9 runs), with an 11 MiB peak.

After `test`, `assert`, regex literals and a regex matcher (`f3beacc`
plus that work, 2026-10-24, two invocations of 3 runs per side): `loop`
0.53–0.71 µs against 4.3–6.8 µs (8–11×), `calls` 0.79–1.2 µs against
6.9–9.2 µs (7–9×), `closures` 0.86–1.2 µs against 7.1–10 µs (8–9×),
`fib` 319–439 µs against 2,118–2,892 µs (6–8×): no change, as the call
path is untouched. Peak memory 15 MiB against 379–457 MiB. Whole runs
as a process (9 runs each, three invocations): `regex/match`, settling
std's `regex` (~840 lines) and compiling and matching 15 regexes,
9.7–14 ms; `testing/asserts` with its tests, 4.5–4.9 ms; peaks
11.3–11.5 MiB. `types/json-syntax-tree` took 13–19 ms this time, but
alternating with the previous commit's binary, 15 runs each, gave the
same times (13.2–16.4 against 13.2–19.9 ms), so that's machine drift.

Flat frames (on `561b4d9`, 2026-10-25): the tree walk replaced by code
lowered per function and run by one loop (history/milestone-3.md). Alternating
the old and new `run-temper` directly, two invocations of 3 rounds of 3
runs each, ranges of per-round medians: `loop` 0.18–0.23 µs against
0.53–0.59 µs for the tree walk (2.5–3×), `calls` 0.32–0.36 against
0.85–0.92 µs (2.5–2.8×), `closures` 0.33–0.37 against 0.90–0.96 µs
(2.6–2.8×), `fib` 137–146 against 336–345 µs (2.4×). Kotlin wasn't
rerun; against its numbers above, that's roughly 15–30×. Whole runs,
mostly settling, didn't change: `types/json-syntax-tree` 9.7–10.5
against 10.0–11.2 ms, `regex/match` 7.0–7.8 against 7.4–8.8 ms,
`testing/asserts` 3.2–3.4 against 3.4–4.2 ms, `algos/myers-diff`
2.4–2.6 against 2.5–3.1 ms, with 14.2–14.3 MiB peaks for both. The gain
is from no `Result` passed up through a recursive `eval` for each node,
locals by slot rather than by walking linked frames to their scope's,
no frame for each block entered, and globals by index. Not tuned yet: a
call still allocates its frame's values and its arguments, and a method
call boxes a `Val::Method`; an operand stack shared by frames,
unboxed method calls and fused ops (a local and a constant) are the
obvious next steps.

Generators (on `25e32ca`, 2026-10-25), alternating the old and new
`run-temper` the same way, three invocations (3 or 5 rounds of 3 runs):
no change beyond noise. `loop` 0.18–0.25 µs against 0.20–0.25 µs for
`25e32ca`, `calls` 0.32–0.37 against 0.31–0.39 µs, `closures` 0.31–0.41
against 0.32–0.37 µs, `fib` 143–150 against 134–153 µs. The call path
gained only a check of the callee's code for a generator. Whole runs'
low ends matched (`types/json-syntax-tree` 10.1 against 10.1 ms,
`regex/match` 7.2 against 7.2 ms, `testing/asserts` 3.2 against 3.3 ms,
`algos/myers-diff` 2.5 against 2.4 ms), though the new binary's high
ends were noisier in two invocations (up to 14.7 against 11.2 ms for
json); 14.2–14.3 MiB peaks for both.

`async`, `await` and promises (on `13f4889`, 2026-10-26), alternating
the old and new `run-temper` the same way, three invocations of 3 rounds
of 3 runs: no change beyond noise, though the machine ran about a
quarter slower than for the rows above, old binary included. `loop`
0.29–0.33 µs against 0.26–0.54 µs for `13f4889`, `calls` 0.44–0.51
against 0.43–0.54 µs, `closures` 0.41–0.49 against 0.41–0.49 µs, `fib`
179–218 against 175–219 µs; whole runs `types/json-syntax-tree`
12.8–17.0 against 13.4–18.0 ms, `regex/match` 8.8–11.4 against
9.0–13.0 ms, `testing/asserts` 4.1–5.7 against 4.2–6.9 ms,
`algos/myers-diff` 3.2–4.7 against 3.2–5.6 ms; 14.2–14.3 MiB peaks for
both. The hot path gained only the `Await` op's arm. `control-flow/async`
and `async-waiters` run as a process in 2.0–2.2 ms (medians of 9; 12.2
MiB peak).

std `net` (on `3fdf7e7`, 2026-10-26), alternating the old and new
release `run-temper` (with `net`) the same way, three invocations of 3
rounds of 3 runs: no change beyond noise, low ends matching. `loop`
0.20–0.33 µs against 0.22–0.31 µs for `3fdf7e7`, `calls` 0.34–0.49
against 0.33–0.44 µs, `closures` 0.31–0.53 against 0.33–0.46 µs, `fib`
151–192 against 145–168 µs; settling all files 178–242 against 171–209
ms; whole runs `types/json-syntax-tree` 10.2–16.5 against 11.1–14.5 ms,
`regex/match` 7.1–14.5 against 7.1–10.2 ms, `control-flow/async`
1.6–3.1 against 1.5–2.1 ms and `async-waiters` 1.5–2.8 against
1.5–2.2 ms. The new binary's high ends were noisier in the second
invocation, as for generators. The hot path is unchanged; the run queue
only gained a check for jobs. `types/netresponse` and
`netresponse-not-found` run as a process, with a request to the local
test server, in 2.1–2.3 ms (medians of 9, three rounds each).

`do … while`, statements in strings, decorated destructuring and
`case` patterns (on `d225dee`, 2026-10-26), all at settle time, so the
interpreter's hot path is untouched. Alternating the old and new release
`run-temper`, two invocations of 3 rounds of 3 runs, ranges of per-round
medians, machine noisy (the second invocation much more so): no change
beyond noise, low ends matching. `loop` 0.27–0.55 µs against 0.20–0.55
for `d225dee`, `calls` 0.43–1.0 against 0.32–1.0 µs, `closures`
0.40–1.5 against 0.32–1.3 µs, `fib` 164–456 against 139–372 µs; settling
all 828 files 206–278 against 170–464 ms; whole runs
`types/json-syntax-tree` 13.2–24 against 9.9–25 ms, `regex/match`
8.7–19 against 8.0–19 ms, `control-flow/async` 1.9–3.4 against
2.1–4.2 ms. Peak memory read 15.1–15.5 MiB for both, alternated six
times (828 files).

Types (on `b0db559`, 2026-10-27), alternating the old and new release
`run-temper` the same way, three invocations of 3 rounds of 3 runs:
typing adds a round of the loop and a checking pass to settling, within
the noise. Settling all 828 files took 167–192 ms against 159–185 ms for
`b0db559`; a build with typing left out entirely read 177–182 ms beside
168–181 ms for `b0db559` and 171–192 ms for typing, so it costs a few
percent at most. Whole runs: `types/json-syntax-tree` 10.1–11.9 against
9.6–11.9 ms, `regex/match` 7.2–8.6 against 6.8–8.8 ms,
`control-flow/async` 1.5–2.3 against 1.5–1.9 ms. The interpreter is
untouched: `loop` 0.18–0.26 against 0.18–0.24 µs, `calls` 0.30–0.36
against 0.29–0.36 µs, `closures` 0.28–0.40 against 0.27–0.35 µs, `fib`
136–152 against 134–152 µs.

Flow facts (on `9f65728`, 2026-10-27), alternating the old and new
release `run-temper` the same way, two invocations of 3 rounds of 3
runs: one more walk over each file before typing, within the noise.
Settling all 828 files took 161–192 ms against 163–193 ms for
`9f65728`. Whole runs: `types/json-syntax-tree` 10.4–15.5 against
10.1–11.7 ms (the high end from one noisy invocation), `regex/match`
7.3–9.5 against 7.1–10.1 ms, `control-flow/async` 1.6–1.9 against
1.6–2.9 ms. The interpreter is untouched: `loop` 0.17–0.26 against
0.16–0.23 µs, `calls` 0.32–0.40 against 0.28–0.43 µs, `closures`
0.28–0.36 against 0.28–0.35 µs, `fib` 133–158 against 126–160 µs.

A whole small run is 8–19 ms for ours (process startup, settling and
1,000–10,000 iterations) and 24–67 ms for Kotlin's in its warm JVM
(compiling and 10–1,000 iterations; JVM startup excluded). Peak memory is
15 MiB for ours and 350–440 MiB for the JVM. Earlier `temper repl`
numbers (6–75 µs per iteration) mostly measured an unwarmed JIT.

So the tree walk was roughly 7–15× faster than Kotlin's warm, but still
slow for an interpreter (roughly 600 ns–1 µs a call or loop turn, where
20–50 ns is typical): it was dynamically typed and untuned. Flat frames
brought that to 180–370 ns; it's still worth profiling once classes and
types settle, since compile-time evaluation runs on it too.

### Binary size and memory

Small is a goal in itself. Low memory matters everywhere: in CI, on small
machines, beside an editor and an LSP, and in watch mode. Binary size
matters too, most of all for wasm, where it's download time for a browser
playground; natively it's mostly startup and install size.
`compat/measure_size.py` measures both. Sizes use the `small` profile
(optimize for size, LTO, stripped; see `Cargo.toml`). Native builds'
panics unwind since 2026-11-09, so the language server and CLI can
catch them; that added 835,584 bytes (11.6%) to the small `tempest`
(7,211,544 to 8,047,128 bytes), a step in its row from then on. The
wasm still aborts, built with its own profile, `small-wasm`, and is
unchanged.
Peak memory is the resident set of one `--bench 1` run: read all 815 Temper
files (~700 KB) and lex and parse them all once.

The rows are `*-small` and `peak-memory-*`, from 2026-10-22 on, with
Kotlin's peaks (`run=kotlin-jvm`) for the lexer and parser.

`tempest test` on the libraries (`c2dbf09` plus the work committed as
`3187787`, 2026-10-27, two runs each, release and small builds alike;
`chromarite-tests`, `ordable-tests`, and their `peak-memory-*` rows):
Chromarite's 56 tests in 0.44–0.56 s at 12.2–12.5 MiB peak, and
Ordable's 29 in 21–22 s at 11.4 MiB, nearly all in its property tests'
natural comparisons, so the interpreter is the cost.

Against Temper-built code (`3187787` plus std built in, since committed
as `12d4576`, 2026-10-27, `ordable-tests` rows by `runner`), with the
machine busy (another job compiling, load ~2): Ordable's tests took 23.0–29.8 s in
tempest's interpreter (release `tempest test`), 16.5–18.9 s as Python
from Kotlin Temper's py backend (`python3 -m unittest`, no JVM startup;
importing is 0.07 s of it), and 9.9–12.2 s as Lua (Lua 5.4). An earlier,
quieter tempest run took 14–22 s. So the interpreter takes roughly 1.5×
Python's time and 2–3× Lua's here. The quiet rerun (2026-10-28, `360f944`,
Ordable's 33 tests, alternating, three rounds each): tempest 52–76 s,
Python 57–60 s, and Lua 25–27 s, so the interpreter now matches Python
and takes 2–3× Lua's time, though its spread is wide.

The wasm rows are `wasm-small` and `wasm-small-gzip`.

The release-profile native binary is 701 KiB. Peak memory includes holding
all the sources at once. `crates/temper-wasm` is a tiny entry point (source
in, tree text out) that's the seed of a browser playground; it's what keeps
the wasm measurement honest, since a binary that reads files gets its whole
parser optimized away on wasm.

The grammar step more than doubles the wasm module. Measured by leaving
parts out: the grammar, its lift and value reading are about 124 KiB, float
parsing from std about 18 KiB, and printing its tree about 47 KiB (float
formatting among it). The grammar is ~100 productions written as Rust
closures, which inline into a lot of code; making `attempt` non-generic
didn't help. The size lever, for later: productions as data, a table of
combinators run by one small matcher, much as Kotlin's are objects. Its
peak memory is the same as the parser's, since `--bench` drops each file's
trees before the next. The settle loop and interpreter add about 90 KiB to
both the native binary and the wasm module. `run-temper`'s peak memory
settles one file at a time, so it's the same as the parser's. Classes, type
parameters, lists, `for … of` and NFKC checks then added about 84 KiB
(`run-temper` 734 → 818 KiB small, 1326 KiB release; wasm 434 → 518 KiB,
30 KiB more gzipped), of which the NFKC tables are roughly 16 KiB of data.
Peak memory didn't move (14.9 MiB, against 193–217 MiB for Kotlin on the
same lexing and parsing). `when`, `?.`, `??` and Float64 then added
20 KiB natively and 39 KiB to wasm (14 KiB gzipped), likely mostly the math
functions the Float64 methods pull in from Rust's libm; memory stayed at
15 MiB. Strings, `StringBuilder`, tags and destructuring `let` then
added 16 KiB natively (1389 KiB release) and 17 KiB to wasm (7 KiB
gzipped); memory stayed at 14.9 MiB, against 199–250 MiB for Kotlin.
Top-level dependency order, `~` and `>>>` then added 8 KiB natively
(1411 KiB release) and 12 KiB to wasm (3 KiB gzipped); memory stayed at
14.9 MiB, against 194–243 MiB for Kotlin.
Modules, `export` and `import` then added 28 KiB natively (1455 KiB
release; `run-temper` also loads imported directories) and 11 KiB to
wasm (4 KiB gzipped); memory 15.1 MiB against 200–248 MiB for Kotlin,
now over 802 files (628 KiB), as the Temper repo has changed.
`Deque`, `DenseBitVector` and several declarations in one then added
4 KiB natively (1469 KiB release) and 4 KiB to wasm (1 KiB gzipped);
memory 15.0 MiB against 196–274 MiB for Kotlin.
`@fun interface`, typed field initializers and locals visible after
their declaration then added under 1 KiB natively (1470 KiB release)
and 2 KiB to wasm (under 1 KiB gzipped); memory 15.1 MiB against
199–240 MiB for Kotlin.
Object literals, trailing blocks, `@auto("==")` and `Empty` then added
24 KiB natively (1500 KiB release) and 18 KiB to wasm (6 KiB gzipped),
more than their few hundred lines suggest; memory 15.1 MiB against
194–236 MiB for Kotlin.
`std` imports, `...`, `Map`, `MapBuilder`, `Pair`, static extensions
and `Date.today()` then added 24 KiB natively (1553 KiB release) and
26 KiB to wasm (9 KiB gzipped); memory 15.0 MiB against 208–237 MiB
for Kotlin.
`test`, `assert`, regex literals, the regex parser and matcher and
std's connected `Regex` methods (~1,100 lines) then added 56 KiB
natively (1662 KiB release) and 55 KiB to wasm (18 KiB gzipped), more
per line than usual: the matcher's closures through `dyn FnMut` and the
parser's error formatting are candidates to look at. Peak memory read
15.5 MiB, but so did the unchanged lexer and parser's (against
192–235 MiB for Kotlin), so that's the environment, not this change.
Flat frames then added 48 KiB natively (1778 KiB release, 116 KiB more)
and 56 KiB to wasm (17 KiB gzipped), for ~1,080 more lines (the
interpreter's 1,454 lines, outside its builtins' modules, became 2,531:
lowering ~1,040, the run loop ~250 and the rest ~1,240). In the release binary's symbols, the interpreter's code
went from 128 to 160 KiB (the run loop 14 KiB, lowering expressions
7 KiB, the layout pass 5 KiB) and hash tables' from 30 to 37 KiB, for
the layout's maps and sets. Peak memory read 13.3 MiB for both the
old and the new binary (802 files), so the drop from the row before is
the environment.
Generators then added 8 KiB natively (1811 KiB release, 33 KiB more)
and 8 KiB to wasm (3 KiB gzipped), for ~210 more lines. Peak memory
read 13.3 MiB for both `25e32ca`'s and the new small binary, alternated
three times (`compat/measure_size.py` read 15.6 MiB, run beside its
JVM oracles).
`async`, `await` and promises then added 8 KiB natively (1825 KiB
release, 14 KiB more) and 7 KiB to wasm (3 KiB gzipped), for ~180 more
lines. Peak memory read 13.2–13.4 MiB for `13f4889`'s small binary and
13.4 MiB for the new one, alternated three times, now over 822 files
(770 KiB), since the inputs now include Chromarite and Ordable; rows before this one read fewer files.
Inherited members, the class checks and private members on any instance
then added 24 KiB natively (1877 KiB release, 52 KiB more) and 22 KiB to
wasm (8 KiB gzipped), for ~600 more lines, many of them building
messages. Alternating `684e37c`'s binaries and the new ones six times
(824 files): peak memory read
14.9–15.4 MiB for both, higher than the row before for both, so the
environment; settling all files took 157–175 ms before and 158–177 ms
after; the interpreter loops were the same within noise (`loop`
0.20–0.23 against 0.19–0.24 µs, `calls` 0.31–0.36 against 0.31–0.41 µs,
`fib` 141–148 against 140–148 µs), and a loop of method calls through an
interface, reading a private field of another instance, took 1.66–1.80
µs before and 1.78–1.99 µs after, maybe 5% slower, though a method call
no longer allocates a `Val::Method`.
std `net`, bodiless connected functions and jobs in the run queue then
added 4 KiB natively without the `net` feature (1880 KiB release, 3 KiB
more) and 9 KiB to wasm, which never has it (3 KiB gzipped), for ~280
more lines. The web client, `ureq` with rustls, ring and bundled root
certificates, is most of a megabyte: with `net`, the default, `run-temper`
is 2307 KiB small and 4400 KiB release, 1217 and 2520 KiB more than
without. Measured with other TLS choices (`3fdf7e7` plus this work, small
and release): `ureq` without TLS, `http` only, adds 232 and 585 KiB, so
rustls, ring and the certificates are ~985 and ~1,935 KiB of it; ureq's
default gzip adds 44 and 85 KiB more, and is left out. `native-tls`
couldn't be built here (no OpenSSL headers); it would link the system's
OpenSSL rather than carry its own. The choice, rustls, keeps `https` in
one static binary with no system libraries (notes/code-style.md); a
`tls` feature apart from `net` is easy to add if the native size comes
to matter. Peak memory read 15.0–15.3 MiB for both `3fdf7e7`'s small
binary and the new one, alternated nine times (824 files), and startup,
running a one-line program, took ~1.0 ms for `3fdf7e7`'s binaries and
for the new ones with and without `net`, small or release (medians of
40, five rounds alternating): the larger binary costs nothing visible.

`do … while`, statements in strings (appending to a `StringBuilder` or
an accumulator type, as Kotlin's `StringExprMacro` does), decorated
destructuring, and `case` patterns (reported as needing types) then
added 8 KiB natively without `net` (1902 KiB release, 22 KiB more), 8 KiB
to `net`'s small binary, and 8 KiB to wasm (3 KiB gzipped), for ~470 more
lines, most of them string accumulation. Peak memory read 15.2–15.3 MiB
for both `d225dee`'s and the new small binary without `net`, alternated
six times (828 files).

Declared and inferred types as facts in the loop, and the first type
errors (history/milestone-4.md; ~830 lines in `types.rs` and `typing.rs`), then
added 20 KiB natively without `net` (1948 KiB release, 46 KiB more), 24
KiB to `net`'s small binary, and 21 KiB to wasm (7 KiB gzipped), against
`b0db559` built the same way (1098, 2315 and 812 KiB). Peak memory read
15.1–15.2 MiB for both, alternated nine times (828 files).

Flow facts for null checks (history/milestone-4.md; ~290 lines in `flow.rs`,
and ~50 more in `typing.rs`) then added 8 KiB natively without `net`
(1961 KiB release, 14 KiB more), 4 KiB to `net`'s small binary, and 8
KiB to wasm (2 KiB gzipped), against `9f65728` built the same way (1118,
2339 and 833 KiB). Peak memory read 15.0–15.4 MiB for the old and
15.2–15.4 MiB for the new, alternated six times (828 files).

Builtin operators' and `console.log`'s signatures, and what may bubble
(history/milestone-4.md; ~400 lines in `typing/signatures.rs` and
`typing/bubbles.rs`, and ~40 more in `typing.rs` and `types.rs`), then
added 12 KiB natively without `net` (1981 KiB release, 19 KiB more), 8
KiB to `net`'s small binary, and 10 KiB to wasm (3 KiB gzipped), against
`e128e4c` built the same way (1127, 2343 and 841 KiB). Alternated with
`e128e4c`'s binaries nine times (828 files): peak memory read 15.0–15.4
MiB before and 15.0–15.5 MiB after, settling all files took 176–192 ms
before and 170–202 ms after, and the interpreter loops and whole runs
were the same within noise.

Std built in (temper-frontend's `stdlib`: `include_str!` of the six
files under `std/`, now `lib/std/`, 89,078 bytes, 24,016 gzipped), against
`3187787` built the same way: the small `tempest` grew 88 KiB (2,423,304
to 2,513,416 bytes; `run-temper` the same, 2351 to 2443 KiB), about the
text itself, and wasm 93 KiB (872,164 to 967,719 bytes), 27 KiB gzipped
(311,207 to 339,265), the text plus ~6 KB of import loading that `run`
now reaches. It isn't behind a cargo feature: a playground needs std to
import anything, so the honest wasm size includes it, and it's a one-time
step, recorded here, rather than code that grows. Peak memory for
settling all files was unchanged (15.8 MiB; 15.9 before).

Builtin types' members read from core (`typing/core_members.rs`,
`b19d7ac` plus this work, 2026-10-28): of the 60 KiB the small
`run-temper` grew without `net`, and 59 KiB of wasm (16 KiB gzipped),
51 KiB is `core.temper`'s embedded text, which loading core as a library
will need anyway. Parsing a type's class is a one-time cost per process,
when typing first needs one of its members: running a two-line program
took 1.4 ms without any, 1.6 ms calling an `Int32` method, and 2.5 ms
calling a `String` one too, whose class is the longest, mostly doc
comments and bodies (small build, medians of 40, alternating). Worth
cutting once core loads as a library, by keeping its parsed summary in
the binary.

`@operator` methods (`0faea27` plus this work, 2026-10-28): `==` and
the comparisons with an object as first operand now look for its class's
marked method, cached per class, so a loop of 600,000 `==` between
objects of a class without one took 100–115 ms against 91–115 ms before
(medians 106 and 100 of 15, alternating, small build): about 10 ns more
each.

### Backends' share

Each backend is a cargo feature of `tempest` and of `temper-wasm`
(`py`, `rust`, and `js`, all on by default), so its share is the small
build's size with all three less without it (`06a7e0a` plus this work,
with JS's source maps, 2026-10-30; `tempest` with `net`). Any one
backend also brings what they share: tmpl's model and validator,
`to_tmpl`, and the generic code they instantiate. Native sizes move in
4 KiB steps, so they're to the nearest step.

| Built with | Small `tempest` | Wasm | Wasm, gzipped |
|---|---|---|---|
| all three backends | 3227 KiB | 1742 KiB | 590 KiB |
| no backends | 2683 KiB | 1201 KiB | 412 KiB |
| `py`'s share | 68 KiB | 70 KiB | 22 KiB |
| `rust`'s share | 120 KiB | 124 KiB | 40 KiB |
| `js`'s share | 76 KiB | 85 KiB | 29 KiB |
| shared by any of them | 272 KiB | 259–262 KiB | 84–85 KiB |

Before this work, the small `tempest` was 3215 KiB and wasm, which had
no backends, 1198 KiB (411 KiB gzipped); `generate` and its JSON add
4 KiB to wasm without backends. JS's source maps added 8 KiB to its
share (68 KiB without). Of the shared part, an unstripped native
build's symbols put 106 KiB in `temper_tmpl` and 24 KiB in `to_tmpl`;
neither is in a build without backends. `run-temper` keeps tmpl for
`--tmpl`.

The `java` and `csharp` features (2026-10-30, on `a4cf8a0` plus the C#
backend and string order by code point), measured the same way, back to
back, against all five backends:

| Built with | Small `tempest` | Wasm | Wasm, gzipped |
|---|---|---|---|
| all five backends | 3399 KiB | 1924 KiB | 649 KiB |
| no backends | 2683 KiB | 1201 KiB | 412 KiB |
| `java`'s share | 76 KiB | 82 KiB | 26 KiB |
| `csharp`'s share | 92 KiB | 96 KiB | 32 KiB |

C#'s share is the larger; about 2.5 KiB of it is Kotlin's `Logging.cs`,
which it carries as text. Where the rest goes wasn't measured. The rows above for `py`, `rust`, and
`js` predate both and weren't measured again.

The `cpp` and `lua` features (2026-10-30, on `0387dfa` plus the Lua
backend), measured the same way, back to back, against all seven
backends:

| Built with | Small `tempest` | Wasm | Wasm, gzipped |
|---|---|---|---|
| all seven backends | 3559 KiB | 2101 KiB | 708 KiB |
| no backends | 2683 KiB | 1201 KiB | 412 KiB |
| `cpp`'s share | 80 KiB | 89 KiB | 31 KiB |
| `lua`'s share | 68 KiB | 83 KiB | 27 KiB |

Lua's is the smallest share yet but Python's, with no source maps and a
small printer. Settle time and peak memory weren't recorded: this
change touches the frontend only in the interpreter's `Boolean`
comparison, and a run without a baseline beside it (274.7–295.0 ms,
and 18.0 MiB with `net`) couldn't be compared with the earlier rows.

std's regex on Rust, Java, C#, and C++ (2026-11-03, `21ca58d` plus
this work): the backends embed Kotlin's support files (`include_str!`
of Rust's `support.rs`, Java's `Core.java`, and C#'s `RegexSupport.cs`
and `IntRangeSet.cs`, 28 KB together) and connect std's regex keys; the
small `tempest` grew 53 KiB (5694 to 5747 KiB), wasm 45 KiB (3659 to
3704 KiB), 15 KiB gzipped (1183 to 1198 KiB), mostly that text. The
`regex` crate is a dependency of std's generated crate, not of tempest,
so it adds nothing here. Peak memory settling all files read 11.7 MiB
(12.1 before; the frontend didn't change).

Measured again on 2026-11-03 (`3d88eec` plus std's `testing` as a
library), the same way, back to back, against all seven backends, with
`measure_size.py --shares`:

| Built with | Small `tempest` | Wasm | Wasm, gzipped |
|---|---|---|---|
| all seven backends | 5667 KiB | 3631 KiB | 1175 KiB |
| no backends | 3190 KiB | 1251 KiB | 425 KiB |
| `py`'s share | 276 KiB | 241 KiB | 77 KiB |
| `rust`'s share | 408 KiB | 363 KiB | 115 KiB |
| `js`'s share | 264 KiB | 236 KiB | 76 KiB |
| `java`'s share | 328 KiB | 257 KiB | 79 KiB |
| `csharp`'s share | 308 KiB | 250 KiB | 79 KiB |
| `cpp`'s share | 408 KiB | 276 KiB | 88 KiB |
| `lua`'s share | 332 KiB | 215 KiB | 68 KiB |
| shared by any of them | 120 KiB | 534 KiB | 165 KiB |

The native shared part, Python alone less no backends less Python's
share, came out smaller than 2026-10-30's 272 KiB: with LTO, code a
backend shares with the rest moves into its share when it's the one
left out, so the native split is rough. Why each grew: "Binary size and
memory", above.

Not yet measured: the full compiler, which will
add the rest of typing and backends.
