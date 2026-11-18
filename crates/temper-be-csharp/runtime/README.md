# The C# runtime

What generated C# needs beside it, begun as the previous compiler's (commit
69f1a5b0) and this compiler's own since; [the backends' internals](../../../docs/internals/detail/06-backends.md#changes-from-kotlin-tempers-runtimes)
list what changed.

- `temper-core/`: the runtime, which `src/runtime.rs` embeds for
  `temper build` to write beside the output.
- `std/`: std's host code (`Regex/`, `Temporal/`, `Net/`), which
  `src/lib.rs`'s `STD_SUPPORT` writes beside std's output.
- `library-template/`: Kotlin's template for each generated library's
  project, which tests check the backend's output against (as
  `src/lib.rs`'s `LOGGING_TEMPLATE`).
