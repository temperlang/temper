# Contributing

Thank you for your interest in Temper! Not all contributions are code:
documentation, examples, visualizations, ideas, and bug reports all
help.

Please follow the [Contributor Covenant code of conduct][CoC] in every
interaction with other contributors and community members; the
[Discord][discord] has notes on following up on conduct problems.

## What's here

This repository is Temper's compiler, written in Rust. Start with:

- [How the compiler works](../internals/README.md): each stage, with
  diagrams and links into the code.
- [Design notes](../../notes/README.md): the plan, the decisions behind
  it, and [where it differs from Temper's first
  compiler](../../notes/kotlin-differences.md).
- [Writing these docs](docs.md): their conventions, how their examples
  are checked, and the plan for rendering them.
- [CI and supply chain](ci.md): what CI pins, and how to bump it.
- [Releasing](releasing.md): tagging a release, release candidates
  first, reviewing what the release workflow makes, and undoing a bad tag.

### Principles

- Match Temper's behavior, error messages, and generated public APIs,
  not its implementation; check against Temper itself (`compat/`).
- Keep it simple: one tree, a settle loop instead of stages, and errors
  as values that still reach code generation.
- No explicit lifetimes where avoidable: ids into arenas, borrows on
  the stack, copies when cheap ([code style](../../notes/code-style.md)).
- Few dependencies, each widely used and quick to compile.
- No unsafe code outside `temper-os` (and `temper-wasm`'s exports):
  [below](#unsafe-code).
- Readable generated code: no numbered names except on real conflicts,
  and source maps for every backend.

### Layout

| Crate | What it does |
|---|---|
| `temper-syntax` | Source to trees: literate files, lexing, parsing, the grammar step |
| `temper-frontend` | Libraries to settled trees: the settle loop, typing, the interpreter, and tmpl translation |
| `temper-analysis` | Editor queries over a settled library: diagnostics, hover, definition, and completion; depends on the frontend only |
| `temper-tmpl` | tmpl, the backend IR ([notes/tmpl/](../../notes/tmpl/README.md)), with shared lowerings such as coroutines |
| `temper-be-*` | One crate per backend, tmpl in and source out, and how its language runs tests, each depending only on `temper-tmpl` |
| `temper-be-testkit` | What the backends' tests share: programs, goldens, and tool discovery |
| `temper-funtests` | Temper's functional tests, in its `cases/` |
| `temper-driver` | The registry of backends, the one compile path, `build`, and running tests with each backend's tools |
| `temper-lsp` | The language server, `temper lsp` |
| `tempest` | The command-line tool, `temper`: argument parsing and output |
| `temper-wasm` | The compiler for the browser, the base of the playground |
| `temper-os` | The OS calls std lacks, behind safe functions: the only crate with unsafe code |

Also: `lib/std/` and `lib/core/` (Temper's std library and core, in
Temper, which the frontend builds in), `docs/` (these docs), `compat/`
(scripts comparing with Temper's first compiler, fuzzing, timing, and
checking the docs), `editors/` (the VS Code extension), `site/` (the
playground), and `notes/`. Each backend crate keeps its
language's runtime in `runtime/`.

## Building

You need [Rust](https://rustup.rs/) 1.85.0 or later (the workspace's
`rust-version`, which CI uses) and, for the scripts,
[uv](https://docs.astral.sh/uv/), which runs them on Python 3.11 or
later.

    cargo build --release -p tempest

That writes the command, `target/release/temper`; the `tempest` crate
builds it. Each backend is a cargo feature, all on by default. To see
one language's code for a library:

    target/release/temper build -w DIR -b py|rust|js|java|csharp|cpp|lua -o OUT

## Testing

    cargo test

runs every crate's tests, needing no other checkout.
[Where tests go](../internals/10-tests.md) says where to add one, and
how to run a few. Backends' tests compare goldens of generated code (`UPDATE_SNAPSHOTS=1` accepts
changes) and run the generated code with each language's own tools,
skipping with a message when a tool is missing;
[test speed](../../notes/test-speed.md) has the details. To run those
too, install the languages' tools: a C++ compiler and CMake, the .NET 6
SDK or later, a JDK 17 or later with Maven, Node.js with npm, Lua 5.4
or LuaJIT with LuaRocks, Python 3.11 or later, and Rust.

On the functional tests in `crates/temper-funtests/cases/` (or, with
`--temper DIR`, a Kotlin Temper checkout's):

    uv run compat/check_functional.py   # the functional tests, in-process
    uv run compat/count_tmpl.py         # which tests translate, and why not

and `compat/check_*.py`, `fuzz_*.py`, and `bench_*.py` for each stage.
For the docs:

    uv run compat/check_anchors.py        # every link leads somewhere
    uv run compat/check_doc_examples.py   # examples run as marked

CI runs formatting, clippy, the link check, every crate's tests with
each language's toolchain (`temper test -b` on small fixture libraries
for every backend among them), the wasm build, and the functional
tests, all on Rust 1.85.0. A nightly job tests C++ on more compilers
and under sanitizers, and runs real libraries' tests on every backend,
and type-checks their generated Python against a ratchet of allowed
errors ([CI](ci.md), "Real libraries' Python types, nightly"), from a
list (`.github/nightly-libraries.json`;
[test speed](../../notes/test-speed.md), "Real libraries, nightly").
[CI and supply chain](ci.md) says what CI pins (actions, crates, and
the checkers of generated code), why, and how to bump a pin.

### Unsafe code

The workspace's lints forbid unsafe code; every crate takes them
(`[lints] workspace = true`), but `temper-os`, which holds the OS
calls std lacks, and `temper-wasm`, whose exports need
`#[unsafe(no_mangle)]`. In `temper-os`, each unsafe block does one
unsafe thing and has a `// SAFETY:` comment saying why it's sound
(clippy checks both), behind a safe function. Adding or removing
unsafe code anywhere fails `temper-os`'s `unsafe_inventory` test
until its `INVENTORY` list says so: update the count for the file,
and say why for a new one.

## Sending changes

Changes come in as GitHub pull requests. A commit to `main` needs two
things:

- **Signed commits** ([commit signing][signing]), which let us produce
  builds with supply chain security metadata, as
  [some large organizations require](https://engineering.homeoffice.gov.uk/standards/signing-code-commits/).
  [Signing commits](signing-commits.md) walks through setting it up.
- **Sign-off** ([`git commit -s`][signoff]), which records who
  authored and authorized each change, so that contributions under
  [GitHub's inbound=outbound][inbound] terms are clear.

A change users would notice gets a note in
[the release notes](../release-notes/README.md#writing-a-release-note).
Docs changes keep the links working (`compat/check_anchors.py`).

## Versions and releases

Temper's version is written once, as the root `Cargo.toml`'s
`[workspace.package] version`; the compiler, runtimes, std, and
generated packages all take it, each in its ecosystem's spelling
([Versions](../internals/detail/06-backends.md#versions-one-version-spelled-for-each-ecosystem)).
Between releases, builds are dev builds named by the commits since the
last release tag (`0.7.0-alpha.0.123`), which `temper --version` shows
with the commit.

Releases are tag-driven: a signed `v` tag on `main` (`v0.7.0-rc.1`,
`v0.7.0`) names the release, and pushing one makes a GitHub release
automatically, with the binaries, the runtimes and std packaged for
each registry, and checksums ([Releasing](releasing.md)). Nothing is
published to package registries yet. CI's builds that say their
version check out the whole history and the tags (`fetch-depth: 0`),
to name dev builds.

## Questions?

- [Discord][discord]: chat with the community.

  > **Note:** Temper's pages link three different Discord invites;
  > this is the one temperlang.dev uses
  > ([inconsistencies](../inconsistencies.md#i2-discord-invites)).

- [Issues](https://github.com/temperlang/temper/issues): report bugs or
  request features.

[CoC]: https://www.contributor-covenant.org/version/3/0/code_of_conduct/
[discord]: https://discord.gg/TY3Be6fzwu
[signing]: https://docs.github.com/en/authentication/managing-commit-signature-verification/about-commit-signature-verification
[signoff]: https://git-scm.com/docs/git-commit#Documentation/git-commit.txt--s
[inbound]: https://docs.github.com/en/site-policy/github-terms/github-terms-of-service#6-contributions-under-repository-license
