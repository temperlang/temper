# 7. The `temper` command

The `temper` command is a thin layer, argument parsing and output, over
the `temper-driver` crate, which holds the registry of backends, the one
way from a settled library to each backend's files, and running tests
with each language's tools; the playground's wasm uses it too.
Temper's two main commands are `test`, which runs a project's tests,
and `build`, which writes every library in every language to
`temper.out`. Temper's std library is built into the
binary, so it needs nothing beside it.

```text
temper test [-b interp|BACKEND]... [--library NAME] [-v] [-w DIR | DIR]
temper build [-w DIR] [-b BACKEND]... [-o DIR] [-v]
temper run --library NAME -b interp|BACKEND [-w DIR]
temper init [-o DIR]
temper serve | lsp
temper version | license | licenses | help [COMMAND]
```

Its commands and flags are Kotlin Temper's `temper`'s, by the same
names and short forms; every one, and how each differs, is in
[the command line](../tools/cli.md). `build`, `test`, and `run` also
take `--ignorefile`, `--no-connected`, and `--logformat json`, which
writes a JSON record per line, as Kotlin's does
([`log`](../../crates/temper-frontend/src/log.rs)). `run` builds the
work root, then runs a library's top level with the backend's tools,
or on the interpreter; `init` writes Kotlin's project skeleton
([`init`](../../crates/temper-driver/src/init.rs)). `watch`, `repl`,
and `doc` say they aren't available yet, and exit with 3.

`serve`, or `lsp`, is a language server for editors, over stdin and
stdout, on the same library finding and `Sources` as `test` and
`build`; it's the `temper-lsp` crate, with
[a page of its own](09-language-server.md).

## Finding libraries

Both commands start the same way. From the directory given, they find
the *work root*: the nearest directory holding `temper.out`, or else
the highest one holding a `config.temper.md`. Under it, each directory
with a `config.temper.md` is a library, and each directory in a library
is a module. Directories that `.gitignore` and `.temperignore` files
list are skipped, as is what `--ignorefile` lists, which has the last
word.

All reading goes through one [`Sources`](../../crates/temper-frontend/src/sources.rs)
value, so the compiler never touches the file system itself. It stacks
layers: the disk, std built in, or files in memory. The playground uses
memory, and the language server puts unsaved editor buffers in front of
the disk. Why, and how the search differs from Kotlin's (ignore files,
configs read rather than run), is in
[the detailed page](detail/07-cli.md#one-sources-value-for-every-read).

## One compile path

```mermaid
flowchart LR
  settled["Settled library"] --> checked["checked: to_tmpl, then validate"]
  checked --> withstd["with_std: std's names, if used"]
  withstd --> translate["translate: one BACKEND"]
  translate --> files["Files"]
```

Callers take these three steps, from
[`temper-driver`](../../crates/temper-driver/src/lib.rs):
[`checked`](../../crates/temper-driver/src/lib.rs) turns a settled library
into tmpl and validates it, [`with_std`](../../crates/temper-driver/src/lib.rs)
gives it std's names as the backend spells them, and
[`translate`](../../crates/temper-driver/src/lib.rs) runs one backend from
the [`Registry`](../../crates/temper-driver/src/lib.rs), which holds each
built-in backend crate's `BACKEND`, one per cargo feature, all on by
default. (`build` sets std's names itself rather than calling
`with_std`.) Which backends `build` writes without `-b`, and which names
this build lacks, come from the registry and the list of
[`KNOWN`](../../crates/temper-driver/src/lib.rs) names, not lists of
their own. The trade-offs are in
[the registry](detail/07-cli.md#a-registry-built-at-startup-from-cargo-features),
[a backend as plain data](detail/07-cli.md#a-backend-is-a-struct-of-function-pointers),
and [one compile path](detail/07-cli.md#one-compile-path), in detail.

## `test` and `build`

```mermaid
flowchart TD
  find["Find libraries"] --> settle["Settle each library"]
  settle -->|"-b interp, the default"| interp["Run its tests on the interpreter"]
  settle -->|"-b BACKEND"| compile["The compile path"]
  compile --> written["Write temper.out/BACKEND"]
  written --> tools["Run the tests with the language's own tools"]
  interp --> tally["One summary of tests passed"]
  tools --> tally
```

- **On the interpreter** (the default), each library settles and runs
  its tests in-process. That's fast: no files are written. Each test
  gets a step budget (`--fuel`, 10 billion by default), so one that loops
  forever fails alone, and what tests log is kept up to 64 KiB.
- **With `-b BACKEND`**, each library is built as `build` does
  ([`test_backend`](../../crates/temper-driver/src/test_run.rs)), then
  its tests run with the language's own tools: Python's
  `temper_core.testing`, Mocha for JavaScript, LuaUnit, JUnit through
  `javac`, `dotnet test`, `cargo test`, or CMake and the test program
  for C++. Each backend crate says how, in its `tools` module, as its
  `BACKEND`'s [`Tools`](../../crates/temper-tmpl/src/backend.rs), and
  the driver just runs them. Their results are read back into the same
  summary, so `-b interp` and `-b py` print alike.
- **With several `-b`**, each backend runs in turn, with its own
  summary, then the total, which is Kotlin's one line.
- **`build`** writes `temper.out/BACKEND/LIBRARY/…` (or under `-o DIR`), laid out as Temper
  always has, with package files (`pyproject.toml`, `Cargo.toml`,
  `package.json`, the pom, the `.csproj`s, and a rockspec), and each
  backend's runtime beside the libraries. A library that imports
  another gets the other's names as that backend named them.
- **Errors never stop either command.** Every error is reported, the
  code is still written, and the tests still run; a test fails only if
  it reaches an error. The exit status is 1 whenever there were errors.
  Each is reported once, under the library it's in, even when other
  libraries import that one.

Each command step by step, the tools each backend runs, and why
imported libraries are settled again in each importer:
[the commands, in detail](detail/07-cli.md#the-commands-step-by-step),
[each language's own tools](detail/07-cli.md#each-languages-own-tools-without-package-managers-where-possible),
and [libraries settled on their own](detail/07-cli.md#each-library-settled-on-its-own-with-what-it-imports).

## Worked example

A library with one file and one test:

```text
triple/config.temper.md     export let name = "triple";
triple/triple.temper
```

```temper
export let triple(n: Int): Int { n * 3 }

test("triple of 14") {
  assert(triple(14) == 42);
}
```

```text
$ temper test -v .
Testing triple
  ok      triple of 14
Tests passed: 1 of 1

$ temper test -b py .
Tests passed: 1 of 1
```

`temper build -b py` writes the library, std, and the runtime under
`temper.out/py/`. The test becomes a `unittest` case, with the
`assert` macro's message built in:

```python
class TestTriple(TestCase):
    def test_triple_of_14(self, /) -> None:
        'triple of 14'
        test: Test = Test()
        try:
            actual = triple(14)
            expected = 42
            test.assert_(actual == expected, lambda: f'expected triple(14) == ({expected}) not ({actual})')
        finally:
            test.soft_fail_to_hard()
```

Add a second test that misspells `triple` as `tripel`, and only that
test fails, on the interpreter and in Python alike:

```text
$ temper test -b py .
triple/triple.temper:8:10: error: tripel has not been declared
triple: 1 error
Testing triple on py
  failed  misspelled - Traceback (most recent call last):
Tests passed: 1 of 2
```

## Where in the code

- [`temper-driver`](../../crates/temper-driver/src/lib.rs): the [`Registry`](../../crates/temper-driver/src/lib.rs), [`KNOWN`](../../crates/temper-driver/src/lib.rs), and the compile path above.
- [`main.rs`](../../crates/tempest/src/main.rs): argument parsing ([`parse`](../../crates/tempest/src/main.rs)), [`build_command`](../../crates/tempest/src/main.rs), [`test_command`](../../crates/tempest/src/main.rs), [`run_library`](../../crates/tempest/src/main.rs), [`init_command`](../../crates/tempest/src/main.rs), and [`serve`](../../crates/tempest/src/main.rs); [`help.rs`](../../crates/tempest/src/help.rs): what `help` prints.
- [`serve`](../../crates/temper-lsp/src/lib.rs): the language server.
- [`build`](../../crates/temper-driver/src/build.rs): every library, each backend, in dependency order.
- [`test_backend`](../../crates/temper-driver/src/test_run.rs) and [`run`](../../crates/temper-driver/src/test_run.rs) in `test_run`: any backend's test tools, as each backend crate's `tools` module gives them ([`Tools`](../../crates/temper-tmpl/src/backend.rs)), with JUnit XML and tool lookup shared in [`tools`](../../crates/temper-tmpl/src/tools.rs).
- [`run`](../../crates/temper-driver/src/run.rs) in `run`: `temper run`, through each backend's `Tools::run`, or on the interpreter; [`init`](../../crates/temper-driver/src/init.rs): `temper init`.
- Tests: `crates/temper-driver/tests/` (dependencies between libraries, and the built-in runtimes).
- [`test_libraries`](../../crates/temper-frontend/src/library_tests.rs), [`test_library`](../../crates/temper-frontend/src/library_tests.rs), and [`Tally`](../../crates/temper-frontend/src/library_tests.rs): tests on the interpreter, and the summary; [`log`](../../crates/temper-frontend/src/log.rs): JSON records.
- [`work_root`](../../crates/temper-frontend/src/project.rs), [`find_libraries_ignoring`](../../crates/temper-frontend/src/project.rs), [`ignore_file`](../../crates/temper-frontend/src/project.rs), and [`load_library`](../../crates/temper-frontend/src/project.rs): finding and loading libraries.
- [`program`](../../crates/temper-frontend/src/stdlib.rs) in `stdlib`: std, built in, as its own library.
- Tests: `crates/tempest/tests/` (the CLI, the layout of `temper.out`, and `licenses`' currency); [all the tests](detail/07-cli.md#tests).

## Dynamic backends (future work)

The registry is a list built at startup, not a table fixed at compile
time, so backends that aren't built in can join it later. The plan:
first as external processes, each given a library as tmpl in a
versioned, serialized form and answering with its files (and running
its tests), so a backend can live in its own repository and language;
then as DLLs, only if processes prove too slow. The wasm build keeps a
fixed list of built-in backends. What has to change first:
[dynamic backends, in detail](detail/07-cli.md#dynamic-backends-planned).

`watch`, `repl`, and `doc` come later
([parity with Kotlin's `temper`](detail/07-cli.md#parity-with-kotlins-temper));
[the next page](08-playground.md) says how `watch` will reuse all this,
and [page 9](09-language-server.md) how `lsp` does.

**In detail:** [The `temper` command in detail](detail/07-cli.md): each
decision with its alternatives and Kotlin Temper's way, the main types,
the tests, and known weaknesses.
