# The REPL: design prep

Part of the [rewrite plan](README.md). A study of Kotlin's `temper repl`
(on main 41a4ff9e, 2026-11-09), from its source, its tests, its docs, and
runs with piped input, and what tempest would need for one. Written
before the decisions below, which override it where they differ.

## Decisions and status (2026-11-11)

The user's answers to the open questions at the end:

- **No `:` commands;** `help`, `describe`, and `translate` stay Temper
  functions. Ctrl-D exits with 0.
- **One module that grows by segments,** each input's scope chained to
  the previous one's, not a module per input with implicit exports.
  Redefinition is shadowing. A later `translate` must show one coherent
  module, shadowed names renamed (`x`, `x__2`).
- **No ordering hacks:** `order.rs` keeps unrelated statements in
  source order; `else` on its own line is a language error, as in a
  file.
- **Type names print as `C`,** qualified only when shadowed
  (`interactive#2.C`); **tempest's diagnostics,** with a snippet.
- **A failed input's declaration is kept,** failing when read, saying
  why; **top-level `var`s carry over**; **the work root loads by
  default** when inside one, `-w` overriding.
- **Values print exactly as Kotlin's,** for the tutorial's transcripts,
  with cycle and depth limits.
- **Robustness:** Ctrl-C stops the input through the interpreter's step
  check; a panic is caught; errors never end the session.
- **Async:** top-level `await`, and the task queue lasts across inputs.
- **rustyline** for line editing (chunk 2), its transitive crates
  too; any other crate is worked around. History in the platform's
  data directory, owner-only, with a way to turn it off; `--color
  auto|always|never` and `NO_COLOR`, with plain ANSI codes.
- **`translate(n, lang)` prints in the REPL** one coherent module,
  shadowed names renamed (`x`, `x__2`); a user's `help` may shadow the
  REPL's, documented.

**Chunk 1, done** ([docs/tools/repl.md](../docs/tools/repl.md)):

- `settle::Incremental` settles one round at a time: each input a file
  of the module `-repl-`, in a scope (a segment) whose parent is the
  previous input's, and the modules it imports as ordinary modules. A
  round's typing and checks walk only its own files and nodes
  (`Settle::round_roots`); earlier rounds are never settled again. A
  top-level `let` in a segment is visible from its end, as a block's
  local, so `let s = "${s}!"` reads the earlier `s`. Object literals
  skip classes a nearer name shadows.
- `interp::repl::Runner` keeps one interpreter: globals grow by each
  module and segment, lowered code stays cached, the run queue lasts.
  An input's top-level `await` lowers as an `async` body's, its file's
  root standing for the function; one still waiting is parked as a
  block on the promise. Steps come in slices of 65,536, between which
  an interrupt flag is checked; with no flag, nothing changes for `run`
  and `test`.
- `repl::ReplSession` (temper-frontend, so the playground and the
  language server can use it): `feed(text)` settles and runs an input,
  giving `ReplEvent`s (diagnostics, logs as they're logged, failures,
  the result) to a sink, in order, each with the text the CLI prints;
  `Chunker` splits lines into inputs by the lexer. A panic is caught
  per input, the round abandoned (`Incremental::abandon_round`).
- `tempest repl`: plain line reading, `-w`, `-ign`, `-e`,
  `--separator`, `--prompt`, `--fuel`, `-v`, `--std`; `-b` says it's
  not yet. SIGINT and the Windows console's Ctrl-C go to a flag through
  the platforms' own calls, no crate.
- Tests: `temper-frontend/tests/repl/*.cases` (transcripts),
  `tests/repl_docs.rs` (the tutorial's and the REPL page's transcripts,
  with markers for pasted blocks), `tempest/tests/repl.rs` (piped runs,
  flags, the work root, Ctrl-C).

**Chunk 2, done** ([docs/tools/repl.md](../docs/tools/repl.md)):

- **rustyline 17.0.2** in `tempest repl` when standard input and output
  are terminals (piped input keeps chunk 1's plain path; so does
  `--separator eof`). 18 and 19 don't build on Rust 1.85 (18 uses
  `File::lock`, 1.89; 19 `cfg_select!`), and none declares an MSRV.
  Features `with-file-history` and `custom-bindings` only.
  `tempest/src/repl/line.rs` is the helper: the `Validator` asks
  `Chunker::completes`, so typed and piped lines split alike; an Enter
  binding inserts the line break and indent (two spaces, two more per
  open bracket) since rustyline has no continuation prompt; the whole
  buffer then goes through a `Chunker`, so pasted lines split as piped
  ones. rustyline takes SIGINT when its editor is made, so the CLI
  installs its own handler again after.
- **Highlighting** by tempest's lexer in the playground's classes,
  with bracket matching; **completion** and a **signature hint** from
  `temper-analysis` over the session (`ReplSession::view` lends the
  settled state and the latest segment): `Analysis` may now borrow a
  `Settled` (`Analysis::of`), its parent and declaration indexes made
  lazily, and `pending.rs` answers for text not settled yet, reading
  it as signature help does and resolving names from the scope.
- **Color** with plain SGR codes (`tempest/src/repl/color.rs`),
  `--color auto|always|never`, `NO_COLOR`, `TERM=dumb`, `CI`; Windows'
  console gets virtual terminal processing through its own calls.
- **History** in the platform's data directory, worked out by hand
  (`history.rs`), 0700 directory and 0600 file, a link refused,
  appended per input, multi-line entries kept whole (rustyline's V2
  format), `--no-history`, leading-space lines not kept.
- **`help`, `describe`, `translate`** (`temper-frontend/src/repl/specials.rs`):
  a prelude segment before input 0 declares them as Temper functions
  calling one bodiless `@connected` function whose name is made a
  temporary, so no input can name it; the interpreter's new `host`
  hook runs it. Bodiless connected functions' defaults don't count
  as optional, hence the bodies. What needs more than the frontend
  comes through `ReplTools`, which `temper-driver/src/repl.rs`
  implements (hover's text, tmpl, backends). `translate` and
  `describe(n, "module")` build one module: input n after the inputs
  it uses (names, and classes of expressions' types, transitively),
  each whole, a name declared again among them renamed `x__2`.
- **Security:** `escape_controls` for what the REPL prints of its own,
  snippets included (marks under the escapes); logs pass through.
- **Leftovers:** input 0's tests run once; a later input's
  `test(...)` says tests don't run in the REPL; a name
  used by an earlier input before a later one declares it gets a note.
- Tests: `specials.cases` (frontend), `temper-driver/tests/repl/tools.cases`,
  unit tests in `tempest/src/repl/{line,color,history}.rs`,
  `tempest/tests/repl.rs` (`color`, `own_functions`,
  `control_characters_are_escaped`, `edits_lines_on_a_terminal` under
  `script`, on Linux).

**Review fixes, done** (an independent review of chunks 1 and 2):

- **History** is opened once and checked: on Unix, the directory must
  be the user's and not a link, made 0700 if it's looser; the file is
  opened by `openat` with `O_NOFOLLOW`, checked by `fstat` (regular,
  the user's, one link), `fchmod`ed to 0600. tempest reads and appends
  it (rustyline's V2 format) through that descriptor, and rustyline
  keeps a `MemHistory`, so nothing reopens the path; `with-file-history`
  is dropped (fd-lock, rustix, and their crates leave the build). On
  Windows, reparse points are refused, the file opened with
  `FILE_FLAG_OPEN_REPARSE_POINT` and checked through its handle.
- **`translate`'s module** takes the earlier inputs that assign to, or
  call a method on, what it includes, to a fixed point; drops an import
  of what an earlier one imported under the same name, rather than
  renaming it in the pattern; and refuses a module with errors, showing
  them, a top-level `await` with how to translate it.
- **Typed and piped alike:** `Chunker::take` drops trailing lines of
  only space, as the indent Enter leaves.
- **A parked input that fails when resumed** fails as itself,
  `interactive#8 failed: …`, its globals noted unset; the input that
  resumed it doesn't fail.
- **Relative imports** resolve from the current directory
  (`Library::relative_from`), not the inputs' `-repl-`.
- **Deep values** drop without recursing (`Drop for Val`, a work list
  of the containers only it owns), for `tempest run -b interp` too.
- **Caps:** a huge string prints cut (`quote_within`); a snippet's line
  is cut to 200 characters around the span.
- **A panic while settling** rolls the round back: the latest segment
  and the REPL module's scope go back, the input keeps its number
  (`Specials::inputs` holds `None` for it), and the modules it loaded
  get globals that say why when read. A panic as it runs leaves what's
  settled; every global is made before anything runs.
- **Escaping:** logs too, when stdout is a terminal; line and paragraph
  separators and invisible characters, joiners only alone or at an
  edge (emoji sequences stay whole), tag characters only outside an
  emoji tag sequence.
- **`--no-workroot`**, and **Ctrl-C** stops `help`, `describe`, and
  `translate`, checked between their steps (`ReplTools` takes an
  `interrupted` callback).

**Left:** `-b`; the playground's REPL tab
([online-repl.md](online-repl.md)); suggestions from history as you
type; `help`'s topics (Kotlin's `unresolved`, `available-imports`);
a translation's module leaves out an earlier input it needs only through
something without a name or a class type, or a change through a call
that isn't a method's, as `f(list)` (rare); and contextual
autoescaping's compile-time pass for REPL inputs (they run unfolded,
which is always safe). Bad string escapes aren't reported anywhere in
tempest yet (they fail when run), a gap outside the REPL.

## 1. How Kotlin's REPL behaves

`temper repl` (also plain `temper`, the default command) reads Temper a
line at a time and runs each complete input, printing its result:

```temper
$ let box = { width: 1.5, height: 0.5 };
interactive#6: void
$ box.width + box.height
interactive#7: 2.0
```

- **Inputs.** A newline outside brackets ends an input. An open `(`, `[`,
  `{`, or string keeps reading, with a blank continuation prompt the
  width of `$ ` and the next line pre-filled with two spaces per open
  bracket (editable). A line of only comments waits too, so a doc
  comment joins the declaration after it. `--separator semisemi` ends
  inputs at `;;` instead (OCaml's), and `--separator eof` runs all of
  standard input as one.
- **Results.** Each input prints `interactive#N: value`, with N counting
  from 0. A trailing `;` gives `void`; a declaration gives `void`, a
  class or interface its type (`Rectangle__0`); a failure (bubble,
  panic, compile error) gives `fail`. Values print as Kotlin's
  interpreter renders them: `"quoted"` strings with Temper escapes,
  lists as `[2,4,6]`, objects as `{class: C__0, a: 3}`, functions as
  `ƒ`. `console.log` output prints as it happens, before the result.
- **State.** Each input's top-level `let`s, functions, and types are
  visible in later inputs. A later `let x` or `class C` shadows the old
  one, even with another type; earlier code keeps the old one (an old
  `Rectangle` instance keeps its old fields). `let` can't be reassigned
  from a later input, and a top-level `var` isn't visible in later
  inputs at all (the tutorial says so).
- **Errors** print as a source snippet with an underline, then
  `[interactive#0:1+0-9]@P: message`: the input, line+column range, and
  the Kotlin stage letter (`@P` parse, `@G` generate code, `@R` run...).
  The input still runs as far as it can (`matchPos` in `ReplTest`: a
  type error, then `interactive#0: void`), and the session goes on.
- **Built-in functions**, not `:` commands: `help()` lists topics,
  `help("topic")` or `help(value)` shows docs (doc comments of user
  functions and types too, and operators: `help(+)`); `describe(n,
  "step")` dumps input n at a frontend step (CST, AST after each stage,
  with types); `translate(n, "py")` shows input n translated for a
  backend, with the files it would write. Topics include `unresolved`
  (pending promises) and `available-imports` (a table of importable
  modules and the `import` line for each).
- **Imports.** `let { Date } = import("std/temporal")` works; the first
  import builds std (in a temporary work root), which takes a while. With
  an explicit `-w DIR`, the work root's libraries are built too and
  importable by library name.
- **Async.** Promises are shared across inputs, and top-level `await`
  is allowed. An `async` block waiting on a `PromiseBuilder` resumes when
  a later input completes it (it printed `got 5` in the input that
  called `complete`). An input whose result is still waiting prints
  `1 promise is unresolved.` (with a one-time hint to `help("unresolved")`)
  and `fail`.
- **Exit:** Ctrl-D (end of input) runs any pending input, saves history,
  and exits 0, whatever failed. There's no quit or reload command.

### Options

`temper repl --help`:

- `-w`/`--workroot DIR` and `-ign`/`--ignorefile FILE`: libraries to
  import (only when `-w` is given; otherwise std only).
- `-b`/`--backend B[:config]`: instead of Temper, build the work root
  for a backend and open that language's own REPL with the libraries
  loaded: `node` for js, `python` for py, `lua`, `jshell` for java. C#
  and Rust say they don't support it.
- `-e`/`--exec-before CODE` (repeatable): inputs to run before the first
  prompt, as for common imports; not added to history.
- `--separator newline|semisemi|eof`, `--prompt normal|hide` (hide for
  batch use).
- One flag per stage, `--Lex`/`-L` through `--Run`/`-R`: dump each input's
  tree after that stage.
- `-v`/`--verbose`: fine logging, including stack traces of panics.

### Terminal

- **Line editing** is JLine's, Emacs keys by default, with history in
  the platform's user data directory (per Temper version,
  `repl-history.txt`), saved on exit; `!` history expansion is off.
  Fish-style suggestions from history appear as you type. Tab completes
  names: the session's (most recent first), then builtins, plus the REPL
  functions' arguments (`describe(_, "` offers step names, `translate(_,
  "` backends, `help("` topics). Ctrl-C drops pending input and gives a
  fresh prompt; during a run it ends the process.
- **Colors** only when standard output is a terminal: Kotlin's `isatty`
  (Jansi), forced off by `TERM=dumb`, `CI`, or `TF_BUILD`, forced on in
  IntelliJ. `NO_COLOR` isn't read, and there's no flag. Errors are red,
  warnings yellow. As you type, JLine highlights the input with the
  Temper lexer: keywords blue, numbers and strings magenta, comments
  green, punctuation cyan, other names underlined, bad tokens on red.
  With piped input there's no highlighting, but the `$ ` prompt still
  prints unless `--prompt hide`.

## 2. How it works inside

`Repl.kt` with jline glue in `ReplInterop.kt`, in Kotlin's `cli` module.

- **Per line:** append to the pending text, lex all of it again
  (quadratic, noted as a TODO), count open brackets and string
  delimiters, and wait if any are open or it's only comments. With
  `;;`, also wait for a final `;;` token, then blank it out.
- **Per input:** the text becomes a module of its own, `-repl//i0007/`,
  in a library `interactive` (so each can be translated alone), and is
  staged through every Kotlin stage to `Run`, under flags
  `moduleResultNeeded` and `allowTopLevelAwait`. Hooks: after
  DisAmbiguate, each top-level declaration is renamed to an exported
  name and marked `@optionalImport`, and the top level is wrapped in a
  block; after SyntaxMacro, declarations hoisted out of that block are
  put back.
- **State across inputs** is a map from base name to the latest
  export, each export holding the value it had when its input ran.
  Each new module implicitly imports all of them; the import stage
  prunes `@optionalImport` ones the input doesn't mention (keeping
  concrete types, for object literals' classes, and std's regex module
  if the input has a regex literal). Only exports with a run-time value
  are kept, which is why a `var`, or a declaration whose input failed,
  doesn't carry over. Redefinition is just a later export of the same
  name winning the map. Types aren't re-checked: each input is typed
  against the exports' recorded types.
- **Imports** go through a feature macro that queues them; between
  stages, the REPL resolves each against modules built by an ordinary
  build of the work root (or a temporary one for std), advanced to
  `Run`, and binds the names.
- **Printing:** after `Run`, it walks the input's tree for error calls
  and logs their problems in source order, warns about unresolved
  promises, then prints `interactive#N: ` and the result. A log sink
  shows each input's location as `interactive#N` and drops two messages
  the result makes redundant. `console.log` goes straight to the
  console. Kotlin's stage snapshots for `describe` go to a temporary
  Berkeley DB in the user cache directory.
- **Robustness:** `processLineRobustly` catches only
  `NotImplementedError`; panics and aborts during staging are caught per
  stage. Anything else ends the process.

### Its tests

`ReplTest` (about 50 cases), `ReplInteropTest` (completion, highlighting,
line parsing), and `ReplChunkIndexTest`. What they pin down, a checklist
for tempest:

- Prompts: `$ `, then `  ` while brackets or a multi-quoted string are
  open; nothing runs until they close; a multi-quoted string's value
  across lines.
- Values and names across inputs: `let`, several at once, classes and
  `new`, static members, interface default methods called through a
  class from another input, generic functions at two types, extension
  and static-extension methods used without naming them, punning
  (`{ b, c: c, d }`) of earlier names.
- Redefinition: `let s = "${s} then after"` sees the old `s`.
- Errors: bad code reports and gives `fail`; a type error still runs
  (`void`); Unicode escape errors at exact spans, without repeats; a
  crash in a builtin (`TODO()`) is caught and the session goes on; a
  bad `import("std")` reports and goes on.
- `console.log` before the result; top-level labeled `break`.
- Imports: std's `testing`, `temporal` (`Date.today()`), `regex` with
  `...` and in regex literals; a work root's library, listed by
  `help("available-imports")`.
- Inputs only import what they use (`describe` shows `x` but not `y`).
- `help` on functions' and types' doc comments, on operators, and its
  topics; `describe` at several steps, with inferred types and type
  arguments; `translate` to C#, JS, Java, Lua, and Python, including
  inputs that failed and types from other inputs.
- Separators: `;;` (mutual recursion over several lines) and `eof`;
  pending input runs at close.
- Completion of names, REPL function arguments, and strings; backslashes
  kept; highlighting of open strings and comments.

## 3. Edge cases and bugs found

Run with piped input, as `temper repl --prompt hide < input`.

- **A cyclic value ends the session.** Printing an object that refers to
  itself overflows the JVM stack while rendering it; the error isn't
  caught, so the REPL exits with status 1 and the session is lost. The
  tutorial's memory chapter mentions the overflow.
- **A function whose body had an error fails silently** when a later
  input calls it: the definition reports the error (and gives `void`),
  but the call prints only `interactive#10: fail`.
- **Reassigning a `let` from a later input** says "`h__0` is reassigned
  after interactive#3 but is not declared `var` at interactive#3",
  naming the wrong input for the declaration, and gives `void`. The
  tutorial still shows an older message and `fail`, and shows class
  results as `Rectangle__0/*new*/`, where the REPL prints
  `Rectangle__0`.
- **A top-level `var` is gone in the next input**: "No declaration for
  v". Documented, but the message doesn't say why.
- **Piped input echoes the auto-indent**: the pre-filled indent is
  printed to standard output (`        interactive#7: void`) and added to
  the input, shifting snippets' columns. `--prompt hide` doesn't stop it.
- **Mutual recursion over two inputs fails** ("No declaration for odd"),
  as names resolve when an input runs; it needs one input or `;;`.
- **`else` on its own line** starts a new input, since the `if` before
  it was complete (from the source; not run).
- **An unclosed `"`** keeps reading the next lines into the same input.
- **A redefined class prints the same name** as the old one
  (`Rectangle__0` for both), though they're different types.
- **Internal names leak**: `C__0`, and in messages, `String__64 |
  MapKey__46 | Equatable__11`.

## 4. What tempest would need

Tempest shouldn't port `Repl.kt`; it should give every feature above,
exactly where scripts or docs depend on it and in spirit elsewhere,
improving where Kotlin's behavior is an accident. The verdict for each
is in brackets.

### One growing library, settled incrementally [improve]

Kotlin makes each input a module that imports snapshots of earlier
exports. Tempest's arena makes something simpler possible: a session
is one `Library` that grows. Each input is appended as a file in a
module of its own, `interactive#N`, whose scope's parent is the previous
input's scope rather than builtins'. Lookups then walk back through
earlier inputs, so:

- Earlier declarations are visible without imports, and redefinition is
  shadowing: the new `x` hides the old for later inputs, while code that
  already resolved to the old `x` keeps it, as in Kotlin.
- The settle loop's own rule, that no resolved name ever changes, is
  exactly the REPL's: earlier inputs never need settling or typing again.
  So "re-typing on redefinition" is nothing to do: a new input is typed
  against the latest bindings, and earlier ones keep their types.
- `var`s carry over, and assignments from later inputs are checked
  against their declared types [improve on Kotlin].

`settle()` takes a whole library and consumes the `Settle` state, so
this needs an incremental entry: keep the `Settle` (engine, scopes,
typing) alive, append the new file's work items, run the loop and the
typing round over just the new nodes, and give back the new
`SettledModule`. Facts already published stay published. The step budget
grows per input. Top-level ordering (`order`) applies within an input
only; the input's last statement, in source order, is its result.

Settling is cheap enough (std's `json`, about 1,400 lines, in about 10
ms), so a fallback, if incremental settling is hard at first, is to
settle the whole session again for each input, and re-run nothing.
But then a run's values must outlive the tree they came from, which the
flat frames' `NodeId`-keyed lowering doesn't allow; incremental is the
real design.

Forward references across inputs (mutual recursion) stay errors
[match], since a later input can't change an earlier one's names; the
error can suggest `;;` or one input [improve].

### The interpreter keeps its state [match in spirit]

`run_with` builds a fresh `Interp` with globals for every module's
scope, lowers each module's top level, and runs them. A session keeps
one `Interp`: on each input, grow `globals` by the new scope's names,
lower only the new top level (earlier functions' lowered code stays
cached, valid because the tree only grows), and run it. The
compile-time `Session` (`interp::compile_time`) already keeps an
interpreter alive across calls and is the natural base. Output streams
to the terminal as it's logged (`interp::run_streaming`'s sink).

- **No fuel by default**, as `tempest run`, but Ctrl-C should stop a
  runaway input and keep the session [improve: Kotlin's Ctrl-C during a
  run ends the process]. The interpreter's fuel check is the place: an
  interrupt flag set from a signal handler zeroes the fuel.
- **Panics in tempest** (bugs) run under `catch_unwind`, as the
  language server does, so a bad input can't end the session [improve].
- **Values whose input failed:** a declaration whose initializer failed
  has no value. Kotlin drops it (the old binding stays visible). Tempest
  could keep it visible and fail at a read with "x has no value:
  interactive#4 failed" (an open question).

### Errors, with recovery [improve]

Tempest already keeps going past errors: an input with errors settles,
types, and runs, and error nodes fail where they're reached, carrying
their message. So the silent `fail` above becomes `Failed: No
declaration for nope` for free. Each input prints its diagnostics, then
its output, then its result. Diagnostics positions are relative to the
input: `interactive#3:1:5`. For snippets, see the open questions.

### Async [match]

The interpreter's task queue and promises stay with the session, so a
block waiting on a promise resumes in the input that completes it. After
each input, run ready tasks until none are; tasks still waiting stay
parked. Top-level `await` must be allowed in REPL inputs (tempest
reports `await` outside a function today): lower an input that awaits as
an `async` body run to completion, or report "unresolved" as Kotlin
does. `help("unresolved")` lists what's waiting.

### Libraries and the work root [match, improve the default]

std is built in, so imports from it need no build and no pause. The
session's `Library.libraries` comes from `project::find_libraries` under
the work root, and an input's `import` loads that module's files into
the arena (`project::load_imports`) and settles them as ordinary
modules, before the input's own module. Kotlin only loads the work root
with an explicit `-w`; tempest could load the work root found as `build`
finds it, which is what someone in a library's directory expects
[improve; open question]. `help("available-imports")` lists them.

`-b BACKEND` (a language's own REPL with the libraries loaded) is a
separate feature over `temper_tmpl::Tools`; later, and only for the
backends that have a REPL [match in spirit].

### Line editing [improve]

Kotlin's features: history, Emacs keys, completion, highlighting, an
editable auto-indent, history suggestions. The Rust candidates, none
added yet (each would need the user's approval, and a check of its
MSRV against the pinned 1.85):

- **rustyline** (MIT): readline-style, small. Has a `Validator` for
  multi-line input (so a whole multi-line input is edited as one), a
  `Highlighter`, `Hinter` (history suggestions), `Completer`, and
  history files. The closest match to Kotlin's.
- **reedline** (MIT, Nushell's): richer (menus, vi mode, a multi-line
  buffer), with more dependencies (crossterm).
- **None:** read lines from standard input, no editing. Needed anyway
  for piped input, so it's the first step.

Either crate stays in the `tempest` binary, never in `temper-frontend`,
so wasm builds don't see it. Incomplete-input detection comes from
tempest's own lexer and bracket balancing, not the editor: open
brackets, an open string, or only comments mean wait [match]. With a
`Validator`, the whole pending input is one editable buffer, and a
pre-filled indent is never echoed or added to the text when input
isn't a terminal [improve].

### Terminal and colors [improve]

The CLI has no color yet. Color when standard output is a terminal
(`std::io::IsTerminal`), not with `NO_COLOR` set, `TERM=dumb`, or `CI`,
plus `--color auto|always|never` [improve: Kotlin reads no `NO_COLOR`
and has no flag]. Errors red, warnings yellow, as Kotlin. Input
highlighting from the lexer's token kinds, as the playground's editor
colors them, so the two agree [match in spirit]. With input that isn't
a terminal, no prompt by default [improve]; `--prompt hide` and
`--separator` still work.

### Output format and the docs [match]

`interactive#N: value` and the value rendering must match exactly:
the tutorial has about 100 transcript lines, and people will diff.
Tempest's `interp::to_string` gives `toString`'s text; the REPL needs
Kotlin's rendering beside it (quoted strings, `[2,4,6]`, `{class: …}`,
`ƒ`, `void`, `fail`), with cycles shown as a marker and depth and
length limits [improve]. Type names: Kotlin prints `C__0`; see the open
questions. The tutorial's transcripts then become tests: a runner that
feeds each `$ ` line to a session and compares what follows, which
also finds docs drift like the one above.

### Help, describe, translate [match in spirit]

Keep them as functions, as in Kotlin, so they're Temper calls that
complete and highlight like any other.

- `help`: topics, doc comments of the session's declarations and std's
  (`temper-analysis` has them for hover), operators.
- `describe(n, what)`: Kotlin's stages don't exist in tempest, so the
  steps are tempest's: `"parse"`, `"settled"` (the settled tree, as
  `print` writes it), `"types"`, `"tmpl"`. Kept per input from the arena,
  so no snapshot database.
- `translate(n, "py")`: through `temper_driver::translate`, which
  already does any backend. A showcase if `translate(n)` gives all seven
  languages at once [improve].

### Options [match]

`-w`, `-ign`, `-b`, `-e`, `--separator`, `--prompt`, `-v` as Kotlin's.
The per-stage dump flags (`--Lex` … `--Run`) name Kotlin's stages, so
they become `describe`'s steps rather than flags [match in spirit].

## 5. Sharing with the playground, the language server, and `run`

One **session** type in `temper-frontend`: the growing library with its
settle state, the interpreter, and per input its source, diagnostics,
output, and result. Each front end is thin:

- **CLI REPL:** a line editor and terminal output around a session.
- **Playground:** a console under the editor. The editor's source is the
  session's first input (or its work root), and each console line is a
  later input against it, so you can call what you wrote. Its fuel and
  depth limits apply per input. `temper-wasm`'s `analyze` already keeps
  the settled source for queries; a session would be the same object,
  kept between console lines.
- **Language server and analysis:** `temper-analysis` answers over one
  `Settled`; over a session, it gives the REPL's completion and hover
  for free (names from all inputs, types, docs). The language server
  could later offer evaluation of a selection, or notebook cells, as
  inputs.
- **`tempest run -b interp`:** a session with one input, the library, and
  no REPL. Sharing the code path keeps `run` and the REPL agreeing about
  output and failures.
- **Tests:** a `repl.cases` file in the settle tests' style, one
  transcript per case, run against a session without a terminal.

## 6. A suggested plan

Each chunk commits on its own.

1. **Incremental settling.** Keep `Settle` alive; append an input as a
   module whose scope chains to the last; settle and type only the new
   nodes. Tests: shadowing, earlier names, `var` across inputs, types
   across inputs, forward references failing, extension methods.
2. **A persistent interpreter.** Grow globals, lower only the new top
   level, keep the task queue; the input's result value; Kotlin's value
   rendering with cycle limits; top-level `await`. Tests: `repl.cases`
   transcripts from `ReplTest`'s checklist.
3. **`tempest repl`, plain.** Reading lines (no editing), detecting
   incomplete input, `--separator`, `--prompt`, `-e`, diagnostics
   relative to each input, Ctrl-C to stop a run, `catch_unwind`, imports
   from std and the work root. Tests: piped runs; the tutorial's
   transcripts as a docs test.
4. **Line editing and color**, with the user's approval of a crate:
   history, completion from `temper-analysis`, highlighting, multi-line
   editing, `NO_COLOR` and `--color`.
5. **`help`, `describe`, `translate`.**
6. **The playground's console** on the same session.
7. Later: `-b BACKEND` for the backends with a REPL; the language
   server's evaluation.

## Open questions

- **Type names in results:** print `C__0` as Kotlin (and the tutorial)
  does, or `C`, adding `(interactive#4)` only when an older `C` is still
  around? The second reads better but changes the docs' transcripts.
- **Diagnostics:** Kotlin's snippet with an underline and
  `[interactive#0:1+0-9]@P:`, or tempest's `path:line:column: level:
  message` with `interactive#0` as the path, plus a snippet? The stage
  letters mean nothing in tempest.
- **A failed declaration:** drop it, keeping the older binding visible
  (Kotlin), or keep it, failing at reads with why?
- **The work root by default:** load the libraries around the current
  directory without `-w`, unlike Kotlin?
- **A line-editing crate:** rustyline, reedline, or none for now?
- **`--color`:** add the flag (Kotlin has none), or only `NO_COLOR`?
- **Meta commands:** stay with Temper functions only, or also accept a
  few `:` commands (`:quit`, `:reset`) that can't clash with Temper?
- **`else` on its own line:** keep Kotlin's rule (a new input), or wait
  when a complete `if` might continue, as some shells do?
