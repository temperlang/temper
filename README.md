<p align="center">
  <img src="docs/images/temper-logo-text-auto.svg" alt="Temper" width="400">
</p>

<p align="center">
  <strong>Write once. Compile to every language.</strong><br>
  A programming language for problem solvers.
</p>

<p align="center">
  <a href="docs/README.md"><b>Docs</b></a> ·
  <a href="docs/guide/tour.md">Quick tour</a> ·
  <a href="docs/guide/getting-started.md">Tutorial</a> ·
  <a href="docs/language/README.md">Reference</a> ·
  <a href="https://temperlang.github.io/tempest/playground/"><b>Playground</b></a>
</p>

---

**Temper** lets a developer or a small team solve a problem once, for
every programming language community. It's a language designed for one
purpose: to translate well to all the others. You write a library in
Temper, and get idiomatic, semantically equivalent libraries in each
language, with their tests, that pass each language's strict checkers.

```temper
export let half(x: Int): Int {
  x / 2
}
```

becomes, in Python,

```py
from temper_core import int_div as _int_div

def half(x: int, /) -> int:
    return _int_div(x, 2)
```

and likewise in each of these[\*](#not-endorsed-or-affiliated):

<table>
  <tr valign="bottom">
    <td align="center"><a href="docs/backends/README.md#c-cpp"><img src="docs/images/lang-icons/cpp.png" alt="C++"><br>C++</a></td>
    <td align="center"><a href="docs/backends/README.md#c-csharp"><img src="docs/images/lang-icons/csharp.png" alt="C#"><br>C#</a></td>
    <td align="center"><a href="docs/backends/README.md#java-java"><img src="docs/images/lang-icons/java.png" alt="Java"><br>Java</a></td>
    <td align="center"><a href="docs/backends/README.md#javascript-js"><img src="docs/images/lang-icons/js.png" alt="JavaScript"> <img src="docs/images/lang-icons/ts.png" alt="TypeScript"><br>JS / TS</a></td>
  </tr>
  <tr valign="bottom">
    <td align="center"><a href="docs/backends/README.md#lua-lua"><img src="docs/images/lang-icons/lua.png" alt="Lua"><br>Lua</a></td>
    <td align="center"><a href="docs/backends/README.md#python-py"><img src="docs/images/lang-icons/py.png" alt="Python"><br>Python</a></td>
    <td align="center"><a href="docs/backends/README.md#rust-rust"><img src="docs/images/lang-icons/rust.png" alt="Rust"><br>Rust</a></td>
    <td align="center">&hellip;<br>and more to come</td>
  </tr>
</table>

## This repository

This repo contains the source for:

- Temper's compiler and core tooling, built as one binary, `temper`, for
  building and testing Temper libraries from
  [the command line](docs/tools/cli.md), and as WebAssembly for use
  in web browsers.
- Temper's core and standard libraries, and each language's runtime
  support.
- Editor support: a language server, `temper lsp`, for any editor, and
  a VS Code extension ([editors/vscode](editors/vscode/README.md)).
- Documentation and website content for Temper.

## Try it

- **In your browser:** the
  [playground](https://temperlang.github.io/tempest/playground/)
  runs the whole compiler as wasm: type Temper and see every
  language's output, errors, and test results as you type.
- **Download** `temper` for Linux, macOS (Apple silicon), or Windows
  from the [releases](https://github.com/temperlang/tempest/releases)
  (the `nightly` prerelease follows `main`), with the VS Code
  extension's `.vsix` files beside it
  ([getting started](docs/guide/getting-started.md#set-up-temper-tools-and-vs-code-support)).
- **From source,** with [Rust](https://rustup.rs/) 1.85 or later:

      cargo build --release -p tempest
      target/release/temper test DIR        # run a library's tests
      target/release/temper build -w DIR    # write temper.out/LANGUAGE/…

  A library is a directory with a `config.temper.md`; the
  [getting started guide](docs/guide/getting-started.md) makes one.
  Temper's standard library is built in, so the binary needs nothing
  beside it.

## Documentation

Everything lives under [docs/](docs/README.md), and on the
[docs site](https://temperlang.github.io/tempest/), beside the
playground:

- [Guide](docs/guide/README.md): why Temper, a quick tour, getting
  started, and a tutorial.
- [Language reference](docs/language/README.md): types, builtins,
  syntax, and modules.
- [Standard library](docs/std/README.md): JSON, regular expressions,
  dates, testing, and networking.
- [Backends](docs/backends/README.md): what each target language gets.
- [Tools](docs/tools/README.md): the command line, editors, and the
  playground.
- [Contributing](docs/contributing/README.md), and
  [how the compiler works](docs/internals/README.md), stage by stage.
- [Release notes](docs/release-notes/README.md).

## Community

- **Discord:** [the Temper Language Discord](https://discord.gg/TY3Be6fzwu)
- **Stack Overflow:** the [`temper`](https://stackoverflow.com/questions/tagged/temper) tag
- **Issues:** [report bugs or request features](https://github.com/temperlang/temper/issues)

## License

[COPYRIGHT](COPYRIGHT) has the details: code is dual licensed as
[Apache 2.0](LICENSE-APACHE) or [MIT](LICENSE-MIT), and documentation
as [CC BY-SA 4.0](LICENSE-CC-BY-SA-4.0). Third-party materials are
listed in [NOTICE](NOTICE).

### Not endorsed or affiliated

We use other programming language projects' logos and names with
permission, but don't imply any official relationship with, or
endorsement by, those projects' contributors or sponsors. (We refer to
them because we love them, as we love all programming languages, their
contributors, and their communities.) Where each logo comes from, and
its terms, is in [docs/images/lang-icons](docs/images/lang-icons/README.md).

Written with Claude Code and Opus 5.5.
