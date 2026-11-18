# The JavaScript runtime

What generated JavaScript needs beside it, begun as the previous compiler's (commit
69f1a5b0) and this compiler's own since; [the backends' internals](../../../docs/internals/detail/06-backends.md#changes-from-kotlin-tempers-runtimes)
list what changed.

- `temper-core/`: the runtime, which `src/runtime.rs` embeds for
  `temper build` to write beside the output.
