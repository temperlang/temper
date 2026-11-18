//! Temper's frontend: from a library's files to a settled tree, and a small
//! interpreter to run it.
//!
//! - [`library`]: one arena per library build, with every file's tree.
//! - [`engine`]: the settle loop's scheduler: work items, facts and waits.
//! - [`settle`]: the settle loop's steps: reading blocks, resolving names in
//!   [`scope`]s and expanding [`macros`] into [`core`] constructs.
//! - `imports`: `let { a } = import("./dir")` across modules.
//! - `json`: `@json`'s generated members and adapter classes.
//! - `extensions`: `@staticExtension`, so `Int.jsonAdapter()` calls a
//!   function, and the record of `@extension`s, which typing calls.
//! - [`regex`]: std's regex model as data, Temper's regex dialect, a
//!   matcher, and regex literals.
//! - `testing`: `test` and `assert`, and running a module's tests.
//! - [`typing`]: declarations' [`types`] as facts in the settle loop,
//!   and type errors.
//! - [`order`]: top-level statements in dependency order.
//! - [`builtins`]: the names modules start with.
//! - `autoescape`: tagged strings' contextual autoescaping worked out at
//!   compile time, as Kotlin's `OptimizeContextualAutoescapingBlocks`.
//! - [`interp`]: an interpreter over core constructs, each function
//!   lowered to a flat form.
//! - [`print`](mod@print): settled trees as text, for tests.
//! - [`sources`]: where a build reads files: the disk, std built in, or
//!   memory, in layers.
//! - [`project`]: libraries on disk, found as `temper test` finds them.
//! - [`repl`]: a REPL session, settling and running one input at a time.
//! - [`library_tests`]: running libraries' tests, for `temper test`.
//! - [`log`]: commands' reports as text or JSON records
//!   (`--logformat`).
//! - [`stdlib`]: Temper's std library, built in.
//! - [`to_tmpl`]: a settled library as tmpl, the backend IR.
//! - `web_client`: HTTP requests for std's `net`, with the `net` feature.

mod autoescape;
pub mod builtins;
mod classes;
pub mod core;
pub mod corelib;
mod dead_branches;
pub mod engine;
mod extensions;
mod flow;
pub mod ignore;
mod imports;
pub mod interp;
mod json;
pub mod library;
pub mod library_tests;
pub mod log;
mod macros;
pub mod order;
pub mod print;
pub mod project;
pub mod regex;
pub mod repl;
pub mod scope;
pub mod settle;
pub mod sources;
pub mod stdlib;
mod testing;
pub mod to_tmpl;
pub mod types;
pub mod typing;
mod web_client;
