package lang.temper.common

/**
 * Borrowed from [OCaml's `ignore`](https://caml.inria.fr/pub/docs/manual-ocaml/libref/Stdlib.html#VALignore).
 *
 * Discards the value of its argument without a verbose annotation.
 */
fun ignore(@Suppress("UNUSED_PARAMETER") x: Any?) = Unit
