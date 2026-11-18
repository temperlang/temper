# The REPL

`temper repl` reads Temper a line at a time and runs each complete
input on the interpreter, against what earlier inputs declared,
printing each result as `interactive#N: value`. It's `temper repl`'s
counterpart; the [tutorial](../guide/tutorial/01-classes-and-functions.md)
uses it throughout.

```console
$ let greet(name: String): String { "Hello, ${name}!" }
interactive#0: void
$ greet("REPL")
interactive#1: "Hello, REPL!"
$ [1, 2, 3].map { x => x * 10 }
interactive#2: [10,20,30]
$ class Point(public x: Int, public y: Int) {}
interactive#3: Point
$ { x: 1, y: 2 }
interactive#4: {class: Point, x: 1, y: 2}
```

Ctrl-D (the end of input) ends the session, with exit code 0 whatever
failed. On a terminal, lines are edited as in a shell, with history,
completion, and color ([below](#on-a-terminal)). Piped input works the
same, without prompts or editing:

```sh
printf '1 + 2\nconsole.log("hi")\n' | temper repl
```

## Inputs

A line ends an input unless something is still open: a bracket (`(`,
`[`, `{`, or `${`), a multi-quoted string (`"""`, ended by a line that
doesn't go on with it, such as a blank one), or a block comment. A line
of only comments waits too, so a doc comment joins what it documents.
Meanwhile the next line, on a terminal, starts indented, two spaces and
two more for each bracket open; piped in, the prompt is `  ` instead of
`$ `. A `"` string never spans
lines: one left open is an error at the end of its line, rather than
swallowing the lines after it.

So `else` on a line of its own starts a new input, as it would be an
error in a file too; keep `} else {` together. To type several
statements as one input, put them on one line, separated by `;`, or
use `--separator semisemi`, which ends inputs at a `;;` at the end of a
line, as OCaml's REPL does; `--separator eof` makes all of the input
one.

## Results

Each input prints what it reports, in order: its errors and warnings,
what it logs as it runs, why it failed if no error already says, and
then its result, as `temper repl` prints it:

| Input | Result |
|---|---|
| An expression | Its value: `3`, `2.0`, `"text"` (with Temper's escapes), `true`, `null`, `[1,2,3]` for a list, `[(1,"a")]` for a map, `{class: Point, x: 1, y: 2}` for an object (its fields), and `ƒ` for a function |
| A trailing `;`, or a declaration | `void` |
| A class or interface | Its name, as `Point` |
| Anything that failed | `fail` |

A value that contains itself prints `↻` where it recurs, and a very deep
or long one ends with `…`, a very long string too (`"yyy…"…`), so
printing never ends the session. A very long line in an error's snippet
is cut to the part around the error, with `…` where it's cut.

## Names

Each input sees the names earlier ones declared. Declaring a name again
makes a new one for later inputs, while code that already used the old
one keeps it, so `let s = "${s}!"` reads the earlier `s`:

<!-- repl: restart -->
```console
$ let s = "first"
interactive#0: void
$ let shout(): String { "${s}!" }
interactive#1: void
$ let s = "${s} and second"
interactive#2: void
$ s
interactive#3: "first and second"
$ shout()
interactive#4: "first!"
```

A class declared again works the same way. Its old instances print
their class with the input that declared it, as `interactive#5.Point`,
when a later `Point` hides it.

A top-level `var` can be assigned from later inputs, checked against its
type. A function can't use a name a later input declares, since an
input's names are settled once; mutually recursive functions go in one
input (on one line, or with `;;`).

## Errors

Errors are reported with the input's number, line, and column, and the
line with the span marked:

<!-- repl: restart -->
```console
$ let total: Int = "many"
interactive#0:1:18: error: Cannot assign to Int32 from String
    let total: Int = "many"
                     ^^^^^^
interactive#0: void
$ total + nope
interactive#1:1:9: error: nope has not been declared
    total + nope
            ^^^^
interactive#1: fail
```

As in a library, an input with errors still runs as far as it can, and
the session goes on. A declaration whose input failed stays declared,
and a later read of it fails, saying why:

```console
$ let half = 1 / 0
interactive#2: fail
$ half + 1
Failed: half has no value: interactive#2 failed
interactive#3: fail
```

## Tests

The first input's tests run once, after it, as `temper test` runs a
library's: each failure, then the summary. What it declares works
either way. So `temper repl --separator eof < file.temper`, or `-e`,
runs a file's tests, then goes on with what it declares:

<!-- repl: restart -->
```console
$ let twice(n: Int): Int { n * 2 }; test("doubles") { assert(twice(2) == 5) }
Test failed: doubles - expected twice(2) == (5) not (4)
Tests passed: 0 of 1
interactive#0: void
$ twice(21)
interactive#1: 42
```

A later input's tests don't run: each is an error.

## Async

Promises and `async` blocks last across inputs, and an input may
`await` at its top level. An input that awaits a promise nothing has
resolved yet prints `1 promise is unresolved.` and `fail`, and goes on
when a later input resolves it:

<!-- repl: restart -->
```console
$ let reply = new PromiseBuilder<String>();
interactive#0: void
$ let answer = await reply.promise
1 promise is unresolved.
interactive#1: fail
$ reply.complete("yes");
interactive#2: void
$ answer
interactive#3: "yes"
```

## The REPL's own functions

Four functions are declared before the first input. They're Temper
functions, so they complete and check as any other, and an input that
declares its own `help`, `describe`, `translate`, or `runTests` hides
the REPL's for the inputs after it, as any redefinition does; there's
no other name to reach them by then.

- **`help()`** says what the REPL offers: these functions, `describe`'s
  steps, the keys, and where the history is. **`help("name")`** shows a
  declaration's signature and doc comment, as hovering in an editor
  does; a function or type also works as itself, `help(greet)`.
- **`describe(n, step)`** shows input `n` at one of the compiler's steps:
  `"parsed"`, the parser's tree; `"settled"` (the default), the tree
  with macros expanded and names resolved, a local as `x:3`;
  `"types"`, each declaration's type, and the result's; `"module"`,
  the Temper module that `translate` translates; and `"tmpl"`, that
  module as tmpl, what the backends translate.
- **`translate(n, lang)`** prints input `n` translated into `cpp`,
  `csharp`, `java`, `js`, `lua`, `py`, or `rust`, each file of its own
  code after a line with its path; `translate(n)` gives every
  language.
- **`runTests()`** runs input 0's tests again, once the input calling
  it ends, as [below](#running-input-0s-tests-again).

What `describe(n, "module")` and `translate(n)` show is one module:
input `n` after the earlier inputs it uses, through the names they
declare, transitively, and the earlier inputs that change what those
declare, as `v = 2;` or `list.add(x);` do, each input whole. Where two
of those inputs declare the same name, the later one's is renamed,
`x__2`, as are the uses that meant it, so the module means what the
inputs did; an import of what an earlier one imported, under the same
name, is dropped instead. A module with errors isn't translated: its
errors are shown, located in the module as `describe(n, "module")`
shows it. A top-level `await` is one, as it runs only in the REPL; to
translate it, put it in an `async` block.

<!-- repl: restart -->
```console
$ let x = 1
interactive#0: void
$ let x = x + 1
interactive#1: void
$ let unused = 0
interactive#2: void
$ x * 10
interactive#3: 20
$ describe(1, "types")
x: Int32
interactive#4: void
```

Input 3 used inputs 0 and 1, and its module renames the second `x`:

<!-- repl: skip -->
```console
$ describe(3, "module")
// interactive#0
let x = 1;
// interactive#1
let x__2 = x + 1;
// interactive#3
x__2 * 10;
interactive#5: void
$ translate(3, "py")
# interactive/interactive/interactive.py
from temper_core import int_add as _int_add, int_mul as _int_mul

__all__ = ()

_x: int = 1
_x2: int = _int_add(_x, 1)
_int_mul(_x2, 10)
interactive#6: void
```

What they show is printed when they run, so an input's other output
comes before or after it as the calls are. A failed call, as
`describe(99)`, fails the input, saying why. Ctrl-C stops them, as it
stops any input.

### Running input 0's tests again

Tests declared in input 0, as `test("name") { … }`, run once, after
it, printing each failure and `temper test`'s summary. `runTests()`
runs them again: their source is settled afresh as a hidden input
after the latest one, so the names they use mean the newest
declarations, and they print the same lines.

<!-- repl: restart -->
```console
$ let twice(n: Int): Int { n * 2 }; test("three") { assert(twice(3) == 6) }
Tests passed: 1 of 1
interactive#0: void
$ let twice(n: Int): Int { n + 1 }
interactive#1: void
$ runTests()
Test failed: three - expected twice(3) == (6) not (4)
Tests passed: 0 of 1
interactive#2: void
```

- **A changed signature** gives type errors, located in input 0, where
  the tests are; the tests still run as far as they can.
- **Old instances keep old classes:** a value made before a class was
  redeclared is still the old class's, with its old methods.
- **Only input 0's tests:** a later input can't declare tests.

## On a terminal

When standard input and output are both a terminal, lines are edited
with [rustyline](https://github.com/kkawakam/rustyline), Emacs-style:

| Key | Does |
|---|---|
| Enter | Runs the input once it's whole; else starts a new line in it, indented |
| Up, Down | Move between an input's lines, then through history |
| Ctrl-R | Searches history |
| Tab | Completes the name being typed |
| Ctrl-C | Drops the input being typed; stops one that's running |
| Ctrl-D | On an empty line, ends the session |

- **Inputs over several lines** are one buffer: Enter runs it only once
  it's whole, by the same rules as piped lines, so an earlier line can
  be edited before it runs. Lines pasted at once split into inputs as
  piped ones do.
- **Text** is edited by what you see: the cursor moves over a
  character with combining marks, an emoji, or a flag as one, and wide
  characters take two columns. Where a terminal draws emoji sequences
  joined by zero-width joiners differs; rustyline guesses from
  `TERM_PROGRAM`.
- **Completion** offers the names in scope, earlier inputs' and std's
  among them, and keywords; after `x.`, the members of `x`'s type, or
  after a type's name, its statics; and in the REPL functions' strings,
  `describe`'s steps, `translate`'s languages, and `help`'s names.
- **A hint** after the cursor, muted, shows the signature of the call
  being typed, as `greet(name: String): String`. It's never inserted.
- **Diagnostics** show as for piped input, colored, once Enter runs the
  input.

### Color

`--color auto`, the default, colors only output to a terminal, and not
when `NO_COLOR` is set (to anything but empty), `TERM` is `dumb`, or
`CI` is set; `--color always` and `--color never` decide outright. Color
applies to the prompt, results (`fail` in red), diagnostics (errors red,
warnings yellow), and input as it's typed, by the classes the
playground uses: keywords, `true`/`false`/`null`/`void`, numbers,
strings (multi-quoted ones too) and their `${…}`, comments, types'
names, decorators, and tokens the lexer rejects. The bracket at the
cursor and its match are marked.

### History

Each input run is kept in a history file, one entry however many lines
it has:

| Platform | File |
|---|---|
| Linux and other Unix | `$XDG_DATA_HOME/temper/repl-history.txt`, or `~/.local/share/temper/repl-history.txt` |
| macOS | `~/Library/Application Support/temper/repl-history.txt` |
| Windows | `%APPDATA%\temper\repl-history.txt` |

What's typed may be private, a token pasted into a string say, so the
file is opened once, checked, and only read and written through what
was opened, never again by its path, so it can't be swapped for a link
to another file as the session goes on:

- **On Unix,** the directory must be the user's own, and not a link;
  if others can read or write it, it's made owner-only (0700). The file
  is opened without following a link, must be a regular file, the
  user's, with no other hard links, and is made owner-only (0600).
- **On Windows,** neither the directory nor the file may be a link or
  a junction (a reparse point); `%APPDATA%` is the user's own by the
  access control lists Windows gives it.

If any of that fails, the session goes on without history, saying why.
A line starting with a space isn't kept, and `--no-history` keeps none
and reads none. `-e` inputs and piped input are never kept. `help()`
says which file the session uses. The file is in rustyline's format:
`#V2`, then an entry a line, its line breaks written `\n`.

## Security

- What the REPL prints of its own, results, diagnostics, failures, and
  its functions' output, has control characters escaped as Temper
  writes them, `\u{1b}`: C0 controls other than line breaks and tabs,
  DEL, C1 controls, the bidirectional controls that reorder text
  (U+061C, U+200E, U+200F, U+202A to U+202E, U+2066 to U+2069), the
  line and paragraph separators (U+2028, U+2029), and invisible
  characters: zero-width spaces and joiners (U+200B, U+FEFF, U+2060 to
  U+2064), fillers, and tag characters. So a value or a pasted snippet
  can't move the cursor, recolor the terminal, set the clipboard, or
  hide text. A zero-width joiner or non-joiner between two visible
  characters stays, so an emoji sequence such as 👩🏽‍💻 prints whole, as
  do tag characters in an emoji flag's tag sequence. Strings print
  quoted, every control and format character escaped, as earlier releases' do.
- What an input logs, with `console.log`, is escaped the same way when
  standard output is a terminal, so a log can't rewrite the screen or
  set the clipboard either; piped or redirected, it's written as
  logged, as `temper run` writes it.
- Importing runs the imported code's top level, as `temper run` does,
  with the interpreter's access to the network through std `net`: one
  of the work root's libraries, or a directory relative to the current
  one ([below](#imports)). The work root is the one around the current
  directory unless `-w` says otherwise, so start the REPL somewhere you
  trust, or pass `--no-workroot`.
- History is kept owner-only ([above](#history)).

## Imports

Inputs import:

- **std's modules,** as `let { Date } = import("std/temporal");`;
- **the work root's libraries,** by name, as `import("greeting")`. The
  work root is `-w`'s, or else the one around the current directory,
  found as `build` finds it, if there is one, and none with
  `--no-workroot`;
- **directories relative to the current one,** where the REPL started,
  as if the inputs' code were there: `import("./shapes")` is the
  directory `shapes` in it, and `import("../lib")` the one beside it.

Importing a module runs its top-level code, once, when the input that
first imports it runs.

## Stopping

Ctrl-C stops the input running, and the session goes on. At the
prompt, it drops the input begun so far: at once on a terminal, or for
piped input, once the line is entered. If `temper` itself is ever stuck,
pressing Ctrl-C again ends it. On Windows, Ctrl-Break does as Ctrl-C
does.

## Options

| Option | Meaning |
|---|---|
| `-w DIR`, `--workroot DIR` | The work root whose libraries inputs import |
| `--no-workroot` | No work root, not even the one around the current directory |
| `-ign FILE`, `--ignorefile FILE` | An [ignore file](cli.md#ignore-files) for the work root |
| `-e CODE`, `--exec-before CODE` | An input to run first; may be given more than once |
| `--separator newline\|semisemi\|eof` | What ends an input ([above](#inputs)) |
| `--prompt normal\|hide` | Whether to prompt; by default, only when standard input is a terminal |
| `--color auto\|always\|never` | Whether to color output ([above](#color)); `auto` by default |
| `--no-history` | Neither read nor keep history ([above](#history)) |
| `--fuel N\|none` | The steps each input may take; none by default |
| `-v`, `--verbose` | Report hints too |
| `--std DIR` | Read std from DIR instead of the copy built in |

`temper repl -b BACKEND`, which opens a backend language's own REPL
with the work root's libraries loaded, isn't available yet, and says
so (exit code 3).

## Compared with earlier releases

The REPL of Temper's earlier releases takes the same inputs, and gives
the same results and `interactive#N:` lines, with these differences:

- **Names:** classes print as `Point`, not `Point__0`, and a top-level
  `var` carries over to later inputs.
- **Errors:** reported as the command line reports them, `interactive#N:line:column:`,
  and a call of a function whose body has an error says why it failed.
- **Robustness:** a cyclic value prints, Ctrl-C stops only the input
  running, and a crash in Temper is reported without ending the
  session.
- **Piped input** gets no prompt, and nothing echoed.
- **`describe`** takes the compiler's steps, not the earlier stage names, and
  **`translate`** prints the code, of input `n` after the inputs it
  uses, rather than writing files; `help`'s topics, as
  `help("available-imports")`, aren't there.
- **The terminal:** a multi-line input is one buffer, and one history
  entry; history is one owner-only file, written as each input runs;
  `NO_COLOR` and `--color` are honored; and what the REPL prints has
  control characters escaped, logs too on a terminal.
- **Imports** relative to the current directory resolve from it, and
  `--no-workroot` turns the work root off.
- **Not yet:** `-b`, and suggestions from history as you type.
