# The Rust runtime

What generated Rust needs beside it, begun as the previous compiler's (commit
69f1a5b0) and this compiler's own since; [the backends' internals](../../../docs/internals/detail/06-backends.md#changes-from-kotlin-tempers-runtimes)
list what changed.

- `temper-core/`: the runtime, which `src/runtime.rs` embeds for
  `temper build` to write beside the output.
- `std/regex/`: std's `regex` support, embedded by `src/lib.rs`.

`temper-core` is a crate of its own, with its own `Cargo.lock`, kept out
of the workspace (the root `Cargo.toml`'s `exclude`); CI runs its tests
with `--manifest-path`. It and std are published as crates of their own,
[`temper-core`](https://crates.io/crates/temper-core) and
[`temper-std`](https://crates.io/crates/temper-std) on crates.io,
rather than packaged inside `temper-be-rust`.
