# The command line

Temper's command line tool is `temper`. Its main commands build a
project's libraries and run their tests; the rest help start, run, and
explore Temper. Its commands and flags keep the names and short forms
of Temper's earlier releases, so scripts written for those still work;
what isn't available yet says so.

| Command | What it does |
|---|---|
| [`build`](#build) | Brings the libraries under the work root up to date, in every target language, under `temper.out/` (or elsewhere, with `-o DIR`) |
| [`test`](#test) | Runs the libraries' tests on each backend given, or on the interpreter by default |
| [`run`](#run) | Builds the work root, then runs a library's top level with a backend's language |
| [`repl`](repl.md) | An interactive shell for Temper (a backend language's own, with `-b`, isn't available yet) |
| [`init`](#init) | Creates a new library: a `config.temper.md` and a first source file |
| [`serve`](#serve-or-lsp) | Starts the language server for an editor, over standard input and output; `lsp` is the same |
| [`version`](#version-license-and-licenses), `license`, `licenses` | About the tool itself |
| [`help`](#help) | The commands, or one command's options |
| [`watch`](#not-available-yet) | Like `build`, but keeps running, rebuilding (and testing, with `-t`) as files change; not available yet |
| [`doc`](#not-available-yet) | Generates documentation for the libraries; not available yet |

> **Note:** what isn't available yet is listed in
> [inconsistencies](../inconsistencies.md#i9-the-command-line).

## Options

Options have a long form and often a short one: `--workroot DIR` or
`-w DIR`, and `--workroot=DIR` too. Every command takes `--help` (`-h`). These mean
the same wherever they're taken:

| Option | Taken by | Meaning |
|---|---|---|
| `-w DIR`, `--workroot DIR` | `build`, `test`, `run`, `repl` | The [work root](#the-work-root) |
| `-ign FILE`, `--ignorefile FILE` | `build`, `test`, `run`, `repl` | An [ignore file](#ignore-files) with the last word |
| `-b BACKEND`, `--backend BACKEND` | `build`, `test`, `run` | A backend: `cpp`, `csharp`, `java`, `js`, `lua`, `py`, or `rust` ([target languages](../backends/README.md)), or `interp`, the interpreter, for `test` and `run`. `build` and `test` take it more than once |
| `--no-connected` | `build`, `test`, `run` | Use the Temper bodies of `@connected` functions, where they have them, instead of each language's own code |
| `-v`, `--verbose` | `build`, `test`, `run` | Say more: what was written, every test's result, what the code logs, and [hints](#diagnostics) |
| `--logformat text\|json` | `build`, `test`, `run` | [How it reports](#logging) |
| `-o DIR`, `--output-directory DIR` | `init`, `build`, `run` | Where to write |
| `--library NAME` | `test`, `run` | A library, by its dashed name (`foo-bar`) |
| `--std DIR` | `build`, `test`, `run`, `repl`, `serve` | Read Temper's std library from DIR instead of the copy built in |
| `--timeout S\|none` | `test`, `run` | Seconds each command a backend's tools start may take ([below](#what-tools-start)) |

Backends of Temper's earlier releases that aren't available yet
(`cppv`, `data`, `java8`, and `mypyc`) [say so](#not-available-yet).

## The work root

Commands work on every library under a *work root*. Each
`config.temper.md` under it starts a library, and each directory in a
library is a module. Without `-w DIR`, the work root is found by
searching up from the current directory for:

1. the nearest directory that contains a `temper.out` directory, or
2. the farthest directory that contains a `config.temper.md`, or
3. the current directory itself.

That's what Temper's earlier releases do too. (Their `--help` says
"farthest" and "closest" the other way around, but their code searches
as above.)

### Ignore files

Files that ignore files list aren't sources. `temper` reads every
`.gitignore` and `.temperignore` as git reads `.gitignore`: the work
root's, those in directories below it, and those above it up to its git
repository's root, a deeper file's patterns after a shallower one's, and
a directory's `.temperignore` after its `.gitignore`, so it has the last
word there. `--ignorefile FILE` adds one more, with the last word over
all of them; its patterns are relative to its own directory, and
match nothing under a work root that isn't at or below
that directory. `--ignorefile /dev/null` adds nothing.

Temper's earlier releases read only one ignore file: `--ignorefile`'s,
or else the nearest `.temperignore` or `.gitignore` at or above the
current directory. On most trees the two agree; reading them all also
skips what a nested `.gitignore` lists, such as a build directory
holding a copy of a library.

## `build`

    temper build [-w DIR] [-ign FILE] [-b BACKEND]... [--no-connected] [-v] [--logformat text|json]
    temper build [same] [-o DIR] [--std DIR]

Writes `temper.out/BACKEND/LIBRARY/` for each library and backend,
with each backend's runtime beside them as `temper-core`. Without `-b`,
it builds C#, Java, JavaScript, Lua, Python, and Rust (earlier
releases also build their `data` backend). With `-o DIR`, it writes
`DIR/BACKEND/LIBRARY/` instead, leaving `temper.out` alone: to look at
one language's code, `temper build -b py -o /tmp/py`.

Errors don't stop it: each is reported, every backend's code is still
written, failing where it runs, and the exit status is 1.

## `test`

    temper test -b BACKEND... [-w DIR] [-ign FILE] [--library NAME] [--no-connected] [-v] [--logformat text|json]
    temper test [-b BACKEND|interp]... [-w DIR | DIR] [same] [--fuel N|none] [--timeout S|none] [--std DIR]

Builds the libraries and runs their tests with each backend's own tools
(Mocha, JUnit, `dotnet test`, `cargo test`, and so on), or on the
interpreter with `-b interp`. It prints the tests that failed, or with
`-v`, every test's result, then a tally, such as `Tests passed: 3 of
3`, and exits with 1 if any test fails or doesn't run.

- **Backends.** Without `-b`, it runs on the interpreter, which takes
  milliseconds and writes no files (earlier releases need `-b`). Given
  several, it runs each in turn, printing each one's tally (`py: Tests
  passed: 3 of 3`) and then the total, the one line earlier releases
  print; it fails if any fails.
- **`--library NAME`** tests only that library, after building them
  all.
- **`DIR`** is where the work root's search starts, in
  place of the current directory; `-w DIR` names the work root itself.
  One or the other.
- **`--fuel N`** is how many steps each test may take on
  the interpreter, and the library's top-level code before them: 10
  billion by default, 3 to 4 minutes of a tight loop. A test that
  runs out fails alone, "ran out of steps (each test may take N)", and
  the others still run, so a test that loops forever doesn't hang the
  run. `--fuel none` lifts the limit; digits may be grouped with `_` or
  `,` (`--fuel 5_000_000_000`). The slowest real library test takes
  about 626 million steps (Kelpto's), a sixteenth of that; `-v` shows
  each test's steps.
  What the tests log is kept up to 64 KiB, then cut with a note, and
  printed only for a library with a failure. Earlier releases have
  neither limit.
- **`--timeout S`** is how long each command a backend's
  tools start may take: 600 seconds by default
  ([below](#what-tools-start)). A tests file on Lua, or a library's
  tests elsewhere, that runs over fails, "timed out after 600 s", and
  the others' results still count. Earlier releases have no limit.
- **Errors in an imported library** are reported once, under it, not
  again for each library importing it.

## `run`

    temper run --library NAME -b BACKEND [-w DIR] [--no-connected] [-v] [--logformat text|json]
    temper run [same] [-ign FILE] [-o DIR] [--timeout S|none] [--std DIR]

Builds the work root for the backend, then runs the library's top
level with that language: Python imports its package, Node imports its
module, Lua runs its `init.lua`, and Rust, C++, Java, and C# build its
program quietly and run it. What it prints goes to standard output as
it runs. With `-b interp`, it runs on the interpreter, writing no
files. Errors fail the run, as in `build`, but don't stop it.

What the program prints streams to standard output, once, as it runs,
on the interpreter too (earlier releases print it after it finishes, on
standard error, and on the interpreter, twice). On the
interpreter, `run` has no step limit, as a program run any other way
has none. On a backend, the program has no time limit unless
`--timeout` or `TEMPER_TIMEOUT` gives one, as it may be a server; the
steps that build it have the usual 600 seconds.

### What tools start

What `test -b` and `run -b` start (`lua`, `python3`, Node and Mocha,
`dotnet`, `java`, `cargo`, CMake, and the programs they build) is
`temper`'s to stop, which earlier releases leave to the tools:

- **A time limit.** Each command may take `--timeout S` seconds, or
  `TEMPER_TIMEOUT`'s, 600 by default; `none` lifts it. One that runs
  over is killed and fails, "timed out after S s", with the end of what
  it printed.
- **Nothing left behind.** Each command runs in a process group of its
  own, which is killed when it ends or times out, and if `temper` gets
  SIGINT (Ctrl-C), SIGTERM, or SIGHUP; so what it started, such as
  `cargo test`'s test binary, goes too. On Linux, each command also
  dies when `temper` does, even by SIGKILL (`PR_SET_PDEATHSIG`); what it
  started survives that one case.
- **A memory limit,** on Linux: Lua, Python, and C++'s test and main
  programs may write at most `TEMPER_MEMORY_LIMIT` MiB, 4096 by
  default (`none` lifts it), so a runaway fails ("not enough memory",
  `MemoryError`) rather than taking the machine. It's `RLIMIT_DATA`,
  which counts memory written, not address space reserved, which the
  JVM, .NET, and Node reserve far more of than they use, so they, and
  the compilers, have none.

Python runs in UTF-8 mode (`PYTHONUTF8=1`), so what it logs reads the
same on every system.

On Windows, a command that runs over is killed, but not what it
started, and nothing dies with `temper` yet. A program run in its own
group isn't in the terminal's foreground, so one that read the
terminal would stop; Temper has no way to read it.

## `init`

    temper init [-o DIR]

Creates a library in `DIR`, by default the current directory, with a
`src/config.temper.md` and `src/NAME.temper.md` to start from
([getting started](../guide/getting-started.md)), where `NAME` is the
directory's name, dashed (`MyProj` gives `my-proj`), byte for byte as
earlier releases write them. It fails, writing nothing, if `DIR`
already has a `src` directory or a `config.temper.md`.

## `serve`, or `lsp`

    temper serve --port N [--tokens add|full|none]
    temper serve [--std DIR]
    temper lsp [--std DIR]

A language server for editors ([editors](editors.md)), over standard
input and output, the way editors usually start servers. Serving on a
TCP port, as earlier releases do with `--port`, isn't available yet:
given `--port`, it says so and exits with 3. It takes `--tokens` but sends no semantic tokens yet.

## `version`, `license`, and `licenses`

    temper version
    temper license
    temper licenses

`version` prints `temper`'s version, the commit it was built from,
and whether that commit's files had changes, as `temper
0.7.0-alpha.0.123 (3f2b063, modified)`; `temper --version` too. Between releases, the version is a dev build named by the
commits since the last release (`alpha.0.123`); the runtimes and std in
`temper.out` name the same version, in each ecosystem's spelling
([Versions](../internals/detail/06-backends.md#versions-one-version-spelled-for-each-ecosystem)).
`license` prints
the copyright, the notices, and the license texts. `licenses` lists the third-party Rust crates built into the
binary, each with its license, made from Cargo's metadata when
`temper` is built.

## `help`

    temper help [COMMAND]
    temper COMMAND --help

The commands, each with a line, or one command's options.

## Not available yet

`watch` and `doc`, `repl -b`, and the backends `cppv`, `data`,
`java8`, and `mypyc`, say they aren't available yet, and exit with 3,
so a script can tell them from a failure:

- **`watch`** waits for a file watcher of its own, which will reuse
  `build` and `test` ([the plan](../internals/detail/08-playground.md#watch-planned)).
  Meanwhile, run `temper build` or `temper test` after a change.
- **`repl -b BACKEND`**, a backend language's own REPL with the work
  root's libraries loaded; [`repl`](repl.md) runs Temper.
- **`doc`** waits for a documentation generator.
- **The backends:** `data` writes data files a library declares, and
  `cppv`, `java8`, and `mypyc` are variants of C++, Java, and Python
  that the backends don't cover yet.

## Diagnostics

Each problem is reported as `path:line:column: level: message`, at one
of three levels:

- **error**: the code is wrong. The library doesn't build, its tests
  count as failed, and the command exits with 1.
- **warn**: worth a look but not wrong, such as an `Int32` literal too
  big to fit, which wraps (for now). The command still
  succeeds.
- **hint**: advice about correct code, such as an unnecessary type
  check. Editors and the playground show hints; the command line says
  them only with `-v`. Earlier releases call these `info` and always
  print them; the JSON records still say `info`.

## Logging

With `--logformat json`, `build`, `test`, and `run` write one JSON
record per line, for tools, all on standard error, in place of their
text (what `run`'s program prints still goes to standard output):

```text
{ "level": "error", "template": "UndeclaredName", "pos": { "loc": "lib/a.temper", "left": 51, "right": 57 }, "values": [], "message": "tripel has not been declared" }
{ "level": "error", "template": "TestFailed", "values": [ "py", "t2", "expected f(2) == (7) not (6)" ], "message": "Test failed (py): t2 - expected f(2) == (7) not (6)" }
{ "level": "summary", "template": "TestSummaryAllRun", "values": [ 1, 2 ], "message": "Tests passed: 1 of 2" }
{ "task": "Test", "ok": false }
```

The records have the shape earlier releases write: `level`, `template`
(the kind of message), `pos` where there is one (the file, and the
start and end in UTF-16 code units), and `values`, ending with the
task's result. Two differences: each record adds `message`, the text it
would have printed, and diagnostics' `values` are empty, as only their
text is kept; their `template` names are new (`UndeclaredName` where
earlier releases say `BecauseNameUndeclared`). The tests' records are
unchanged.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Success |
| 1 | Failure: errors in the code, a test that failed or didn't run, a library that didn't build, or a backend this build of `temper` was built without |
| 2 | A usage error: an unknown command or option, a missing value, or a backend that doesn't exist |
| 3 | Not available yet: `watch`, `doc`, `repl -b`, a backend of earlier releases, or `serve --port` |
| 101 | A bug in Temper: it panicked, saying where on standard error. Please report it with what you ran |

Earlier releases exit with 1 on failure and 127 on a usage error,
which shells otherwise use for a command that wasn't found.

## Gone: one backend's files

Development builds of this compiler had a command per backend, `py
FILE...` and so on, printing one language's code for loose files.
Temper's command line has no such commands, so they're gone: make a
library (`temper init`, or a
`config.temper.md` beside the files) and run `temper build -b py -o
DIR`, which writes the same files, laid out as `temper.out/py`.
